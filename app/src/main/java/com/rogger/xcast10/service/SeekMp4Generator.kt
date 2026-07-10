package com.rogger.xcast10.service

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 10/07/2026
 * Hora: 19:40
 */
/*
 * NOVO ARQUIVO — substitui a estratégia de "corte por byte" do antigo seekByRestarting().
 *
 * Em vez de calcular um offset proporcional e pular bytes crus do arquivo original (o que
 * produz um MP4 inválido, sem os átomos ftyp/moov corretos e sem começar num keyframe — por
 * isso a LG webOS respondia HTTP 500 ou simplesmente ignorava o SetAVTransportURI), esta
 * classe usa o FFmpeg para gerar um NOVO arquivo MP4, estruturalmente válido, que já começa
 * exatamente no ponto solicitado.
 *
 * Estratégia:
 *  1) "-ss <tempo> -i <entrada>"  -> seek de ENTRADA (rápido, feito pelo demuxer), que o
 *     FFmpeg resolve automaticamente para o keyframe mais próximo igual ou anterior ao
 *     tempo pedido. É isso que garante que o arquivo de saída comece num keyframe.
 *  2) "-c copy"                   -> remuxa sem recodificar (rápido, sem perda de qualidade).
 *     Só é abandonado (fallback) se o remux falhar.
 *  3) "-movflags +faststart"      -> reescreve o átomo moov para o início do arquivo, que é
 *     o que players "progressive download" como o da LG webOS esperam para começar a tocar
 *     sem precisar buscar o final do arquivo primeiro.
 *  4) "-avoid_negative_ts make_zero" -> corrige timestamps que ficariam negativos por causa
 *     do corte, evitando travamentos/avisos em players mais rígidos como o da webOS.
 */

import android.content.Context
import android.net.Uri
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Resultado da geração do MP4 recortado. */
sealed interface SeekFileResult {
    data class Success(val file: File) : SeekFileResult
    data class Error(val message: String) : SeekFileResult
}

/**
 * Responsável, exclusivamente, por transformar (arquivo original + posição em ms) num novo
 * arquivo `.mp4` válido, iniciando num keyframe, pronto para ser servido pelo [LocalHttpServer].
 *
 * Não conhece DLNA nem HTTP — a única responsabilidade é gerar o arquivo corretamente.
 */
object SeekMp4Generator {

    private const val TAG = "SeekMp4Generator"

    // Nome fixo: garante que nunca existem dois arquivos temporários de seek "vivos" ao mesmo
    // tempo (requisito de não criar múltiplos arquivos simultaneamente) — cada nova chamada
    // sempre sobrescreve/apaga o mesmo destino.
    private const val OUTPUT_NAME = "xcast10_seek_temp.mp4"

    /**
     * Gera um novo MP4 começando em [startPositionMs]. Apaga qualquer arquivo temporário
     * anterior antes de começar. Retorna [SeekFileResult.Success] com o arquivo pronto, ou
     * [SeekFileResult.Error] com uma mensagem amigável em caso de falha.
     */
    suspend fun generate(
        context: Context,
        sourceUri: Uri,
        startPositionMs: Long
    ): SeekFileResult {
        val outputFile = outputFile(context)

        // Requisito: apagar o temporário anterior automaticamente antes de criar o próximo.
        deleteQuietly(outputFile)

        val input = try {
            resolveInputParam(context, sourceUri)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao resolver o arquivo de origem", e)
            return SeekFileResult.Error("Não foi possível acessar o vídeo de origem: ${e.message}")
        }

        val ss = formatSeconds(startPositionMs)

        return try {
            // Tentativa 1 (preferencial): remux sem recodificar.
            val copyCommand = buildCommand(input, ss, outputFile, reencode = false)
            val copyOk = runFFmpeg(copyCommand)

            if (copyOk && isValidOutput(outputFile)) {
                Log.d(TAG, "MP4 gerado com -c copy (${outputFile.length()} bytes)")
                return SeekFileResult.Success(outputFile)
            }

            Log.w(TAG, "Remux com -c copy falhou ou gerou arquivo inválido; tentando recodificar")
            deleteQuietly(outputFile)

            // Tentativa 2 (fallback): recodifica. Só acontece nos casos em que o container/
            // codec de origem não permite corte limpo por keyframe via stream copy (raro, mas
            // possível em arquivos com GOPs muito longos ou containers exóticos).
            val reencodeCommand = buildCommand(input, ss, outputFile, reencode = true)
            val reencodeOk = runFFmpeg(reencodeCommand)

            if (reencodeOk && isValidOutput(outputFile)) {
                Log.d(TAG, "MP4 gerado com recodificação (${outputFile.length()} bytes)")
                return SeekFileResult.Success(outputFile)
            }

            deleteQuietly(outputFile)
            SeekFileResult.Error("O FFmpeg não conseguiu gerar o vídeo a partir desse ponto")
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao gerar MP4 de seek", e)
            deleteQuietly(outputFile)
            SeekFileResult.Error("Erro inesperado ao preparar o vídeo: ${e.message}")
        }
    }

    /** Apaga o temporário de seek, se existir. Chamado ao parar a transmissão. */
    fun deleteTemp(context: Context) = deleteQuietly(outputFile(context))

    private fun outputFile(context: Context): File =
        File(context.cacheDir, OUTPUT_NAME)

    private fun isValidOutput(file: File): Boolean = file.exists() && file.length() > 0

    private fun deleteQuietly(file: File) {
        if (file.exists()) {
            val ok = file.delete()
            Log.d(TAG, "Removendo temporário anterior ($ok): ${file.absolutePath}")
        }
    }

    /**
     * Resolve o parâmetro de entrada para o FFmpeg a partir do [Uri] do vídeo.
     * Uris `content://` (galeria/MediaStore/SAF) são resolvidas via o parâmetro SAF do
     * FFmpegKit, que permite ao FFmpeg ler diretamente do ContentResolver sem precisar copiar
     * o arquivo inteiro antes. Uris `file://` usam o caminho direto.
     */
    private fun resolveInputParam(context: Context, uri: Uri): String {
        return when (uri.scheme) {
            "content" -> FFmpegKitConfig.getSafParameterForRead(context, uri)
                ?: throw IllegalStateException("SAF não retornou um parâmetro de leitura válido")
            "file" -> uri.path ?: throw IllegalStateException("Caminho de arquivo inválido")
            else -> uri.toString()
        }
    }

    private fun formatSeconds(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0)) / 1000.0
        return String.format(Locale.US, "%.3f", seconds)
    }

    private fun buildCommand(
        input: String,
        startSeconds: String,
        output: File,
        reencode: Boolean
    ): String {
        val codecArgs = if (reencode) {
            // Recodifica apenas como último recurso, mantendo compatibilidade ampla com a LG webOS.
            "-c:v libx264 -preset veryfast -profile:v main -c:a aac -b:a 128k"
        } else {
            "-c copy -map 0"
        }

        return "-ss $startSeconds -i \"$input\" $codecArgs " +
                "-avoid_negative_ts make_zero -movflags +faststart -y \"${output.absolutePath}\""
    }

    /**
     * Executa o FFmpeg de forma assíncrona e suspende a coroutine até o processo terminar,
     * em vez de usar delay() fixo — a conclusão real do processo é o único gatilho.
     */
    private suspend fun runFFmpeg(command: String): Boolean =
        suspendCancellableCoroutine { cont ->
            var currentSession: FFmpegSession? = null
            currentSession = FFmpegKit.executeAsync(command) { session ->
                val success = ReturnCode.isSuccess(session.returnCode)
                if (!success) {
                    Log.e(TAG, "FFmpeg retornou falha (${session.returnCode})")
                }
                if (cont.isActive) cont.resume(success)
            }
            cont.invokeOnCancellation {
                currentSession?.cancel()
            }
        }
}
