package com.rogger.xcast10;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentControloBinding;

/**
 * Fragmento responsável pela interface de controlo da reprodução de vídeo na TV.
 * Permite ao utilizador pausar, reproduzir, ajustar o volume e controlar o progresso do vídeo.
 */
public class ControloFragment extends Fragment {
    private String deviceUrl;
    private boolean isPlaying = true;
    private int currentVolume = 50;
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

        deviceUrl = getArguments().getString("deviceUrl");
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

            binding.btnVolumeUp.setOnClickListener(v -> {
                currentVolume = Math.min(50, currentVolume + 5);
                DLNAManager.sendCommand(deviceUrl, "SetVolume", "<Channel>Master</Channel><DesiredVolume>" + currentVolume + "</DesiredVolume>");
            });

            binding.btnVolumeDown.setOnClickListener(v -> {
                currentVolume = Math.max(0, currentVolume - 5);
                DLNAManager.sendCommand(deviceUrl, "SetVolume", "<Channel>Master</Channel><DesiredVolume>" + currentVolume + "</DesiredVolume>");
            });

            binding.videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Converter progresso para tempo (exemplo simplificado: progresso é % de 1 hora)
                        String targetTime = formatTime(progress * 36);
                        DLNAManager.sendCommand(deviceUrl, "Seek", "<Unit>REL_TIME</Unit><Target>" + targetTime + "</Target>");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
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
