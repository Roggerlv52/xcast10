package com.rogger.xcast10;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentControloBinding;

/**
 * Fragmento responsável pela interface de controlo da reprodução de vídeo na TV.
 * Permite ao utilizador pausar, reproduzir, ajustar o volume e controlar o progresso do vídeo.
 * Exibe o título do vídeo que está a ser transmitido e permite avançar/retroceder.
 */
public class ControloFragment extends Fragment {
    private String deviceUrl;
    private String renderingControlUrl;
    private String videoTitle;
    private long durationMs;
    private boolean isPlaying = true;
    private int currentVolume = 30; // Volume inicial padrão
    private FragmentControloBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentControloBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            deviceUrl = getArguments().getString("deviceUrl");
            renderingControlUrl = getArguments().getString("renderingControlUrl");
            videoTitle = getArguments().getString("videoTitle");
            durationMs = getArguments().getLong("durationMs", 0);
        }

        if (videoTitle != null) {
            binding.tvVideoTitle.setText(videoTitle);
        }

        // Configurar o SeekBar com a duração real (em segundos)
        if (durationMs > 0) {
            binding.videoSeekBar.setMax((int) (durationMs / 1000));
        }

        if (deviceUrl != null) {
            binding.btnCancel.setOnClickListener(v -> {
                // Enviar comando Stop para a TV antes de sair
                DLNAManager.stop(deviceUrl);
                
                // Voltar para o ecrã inicial
                NavHostFragment.findNavController(ControloFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            });
            
            binding.btnPausePlay.setOnClickListener(v -> {
                if (isPlaying) {
                    DLNAManager.sendCommand(deviceUrl, "Pause", "");
                    binding.btnPausePlay.setText("Reproduzir");
                } else {
                    DLNAManager.sendCommand(deviceUrl, "Play", "<Speed>1</Speed>");
                    binding.btnPausePlay.setText("Pausar");
                }
                isPlaying = !isPlaying;
            });

            // Configuração de Volume
            binding.btnVolumeUp.setOnClickListener(v -> {
                currentVolume = Math.min(100, currentVolume + 5);
                updateVolume();
            });

            binding.btnVolumeDown.setOnClickListener(v -> {
                currentVolume = Math.max(0, currentVolume - 5);
                updateVolume();
            });

            binding.videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    String targetTime = formatTime(progress);
                    DLNAManager.seek(deviceUrl, targetTime);
                }
            });
        }
    }

    private void updateVolume() {
        String targetUrl = (renderingControlUrl != null) ? renderingControlUrl : deviceUrl;
        String args = "<Channel>Master</Channel><DesiredVolume>" + currentVolume + "</DesiredVolume>";
        
        // Envia o comando para o serviço de RenderingControl
        DLNAManager.sendRenderingCommand(targetUrl, "SetVolume", args);
        
        // Feedback visual para o utilizador
        Toast.makeText(requireContext(), "Volume: " + currentVolume + "%", Toast.LENGTH_SHORT).show();
    }

    private String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
