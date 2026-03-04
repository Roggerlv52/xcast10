package com.rogger.xcast10.util;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;

public class DialogUtil {

    /**
     * Interface de callback para capturar as ações do diálogo.
     */
    public interface DialogCallback {
        void onConfirm();
        default void onCancel() {
            // Opcional: implementar se precisar de ação no cancelamento
        }
    }

    /**
     * Exibe um alerta simples com botões de Confirmação e Cancelamento.
     */
    public static void showDialog(Context context, String title, String message, DialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.onConfirm();
                }
            }
        });

        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.onCancel();
                }
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Exibe um alerta apenas com o botão de fechar (Aviso).
     */
    public static void showAlert(Context context, String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Fechar", null)
                .show();
    }
}
