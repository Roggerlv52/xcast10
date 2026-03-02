package com.rogger.xcast10;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentFirstBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragmento inicial do aplicativo.
 * Responsável por descobrir Smart TVs na rede, listar vídeos locais do dispositivo
 * e iniciar o servidor HTTP para streaming.
 */
public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private DLNADevice selectedDevice;
    private LocalHttpServer server;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Tenta recuperar o dispositivo guardado anteriormente no DLNAManager
        selectedDevice = DLNAManager.getSelectedDevice();
        if (selectedDevice != null) {
            binding.tvStatus.setText("TV Conectada: " + selectedDevice.getName());
            binding.btnSelectVideo.setEnabled(true);
        }

        binding.btnDiscover.setOnClickListener(v -> {
            binding.tvStatus.setText("Buscando Smart TVs...");
            DLNAManager.discoverDevices(requireContext(), new DLNAManager.DiscoveryCallback() {
                @Override
                public void onDeviceFound(DLNADevice device) {
                    getActivity().runOnUiThread(() -> {
                        selectedDevice = device;
                        // Guarda o dispositivo selecionado para persistência
                        DLNAManager.setSelectedDevice(device);
                        binding.tvStatus.setText("TV Encontrada: " + device.getName());
                        binding.btnSelectVideo.setEnabled(true);
                        Toast.makeText(getContext(), "Conectado a " + device.getName(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFinished(String msg) {
                    getActivity().runOnUiThread(() -> {
                        if (selectedDevice == null) {
                            binding.tvStatus.setText(msg);
                        }
                    });
                }
            });
        });

        binding.btnSelectVideo.setOnClickListener(v -> {
            if (selectedDevice == null) {
                Toast.makeText(getContext(), "Selecione uma TV primeiro", Toast.LENGTH_SHORT).show();
                return;
            }
            showVideoSelectionDialog();
        });
    }

    /**
     * Exibe um diálogo com a lista de vídeos locais encontrados no dispositivo.
     */
    private void showVideoSelectionDialog() {
        List<VideoItem> videoList = getVideoList(requireContext());
        if (videoList.isEmpty()) {
            Toast.makeText(getContext(), "Nenhum vídeo encontrado no dispositivo", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Selecione um Vídeo");

        VideoAdapter adapter = new VideoAdapter(requireContext(), videoList);
        builder.setAdapter(adapter, (dialog, which) -> {
            VideoItem selectedVideo = videoList.get(which);
            startStreaming(selectedVideo);
        });

        builder.show();
    }

    /**
     * Obtém a lista de vídeos do MediaStore com metadados (título, duração, miniatura).
     */
    private List<VideoItem> getVideoList(Context context) {
        List<VideoItem> list = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION
        };

        try (Cursor cursor = contentResolver.query(uri, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME));
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                    long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));

                    // Garante que a duração seja capturada corretamente (alguns arquivos podem retornar 0 inicialmente)
                    if (duration <= 0) {
                        try {
                            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                            retriever.setDataSource(path);
                            String time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (time != null) duration = Long.parseLong(time);
                            retriever.release();
                        } catch (Exception e) {
                            Log.e("VideoList", "Erro ao obter duração manual para " + name);
                        }
                    }

                    list.add(new VideoItem(id, name, path, duration));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("VideoList", "Erro ao listar vídeos", e);
        }
        return list;
    }

    /**
     * Inicia o servidor HTTP local e envia o comando de reprodução para a Smart TV.
     */
    private void startStreaming(VideoItem video) {
        try {
            if (server != null) server.stop();
            server = new LocalHttpServer(8080, video.getPath(), requireContext());
            server.start();

            String ip = DLNAManager.getLocalIpAddress();
            String videoUrl = "http://" + ip + ":8080/video.mp4";

            // Envia o comando para a TV
            DLNAManager.setAVTransportURI(selectedDevice.getServiceUrl(), videoUrl);

            // Navega para o ecrã de controlo passando os dados do vídeo
            Bundle bundle = new Bundle();
            bundle.putString("deviceUrl", selectedDevice.getServiceUrl());
            bundle.putString("renderingControlUrl", selectedDevice.getRenderingControlUrl());
            bundle.putString("videoTitle", video.getTitle());
            bundle.putLong("durationMs", video.getDurationMs());

            NavHostFragment.findNavController(FirstFragment.this)
                    .navigate(R.id.action_FirstFragment_to_SecondFragment, bundle);

        } catch (Exception e) {
            Toast.makeText(getContext(), "Erro ao iniciar streaming: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
