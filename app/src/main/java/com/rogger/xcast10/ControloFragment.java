package com.rogger.xcast10;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentControloBinding;
import com.rogger.xcast10.util.DialogUtil;
import com.rogger.xcast10.util.SharedPreference;

import java.util.Locale;

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
    private int currentVolume = 10; // Volume inicial padrão
    private FragmentControloBinding binding;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateProgressRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentControloBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            deviceUrl = getArguments().getString("deviceUrl");
            DLNAManager.play(deviceUrl);
            renderingControlUrl = getArguments().getString("renderingControlUrl");
            videoTitle = getArguments().getString("videoTitle");
            durationMs = getArguments().getLong("durationMs", 0);
            SharedPreference.setString(requireContext(),String.valueOf(R.string.key_preference),videoTitle);
        }

        if (videoTitle != null) {
            binding.tvVideoTitle.setText(videoTitle);
            binding.txt.setText("Time: " + formatDuration(0)); // inicia no 0
        }

        // Configurar o SeekBar com a duração real (em segundos)
        if (durationMs > 0) {
            binding.videoSeekBar.setMax((int) (durationMs / 1000));
        }

        if (deviceUrl != null) {

            binding.btnCancel.setOnClickListener(v -> {
                canselStreaming();
            });

            // Botão Play/Pause
            binding.btnPausePlay.setOnClickListener(v -> {
                if (isPlaying) {
                    DLNAManager.pause(deviceUrl);
                    binding.btnPausePlay.setText("Reproduzir");
                } else {
                    DLNAManager.play(deviceUrl);
                    binding.btnPausePlay.setText("Pausar");
                }
                isPlaying = !isPlaying;
            });

            // Volume
            binding.btnVolumeUp.setOnClickListener(v -> {
                currentVolume = Math.min(100, currentVolume + 1);
                updateVolume();
            });
            binding.btnVolumeDown.setOnClickListener(v -> {
                currentVolume = Math.max(0, currentVolume - 1);
                updateVolume();
            });

            // SeekBar
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
                    String targetTime = formatDuration(progress * 1000L);

                    // Envia comando Seek correto
                    DLNAManager.seek(deviceUrl, targetTime);
                    // Força Play visual
                    isPlaying = true;
                    binding.btnPausePlay.setText("Pausar");
                }
            });

            // Inicia atualização automática do SeekBar
            startProgressUpdater();
        }
        // Cria o callback para o botão voltar
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DialogUtil.showDialog(requireContext(),
                        getString(R.string.title_exit),
                        getString(R.string.msg_exit), new DialogUtil.DialogCallback() {
                    @Override
                    public void onConfirm() {
                        setEnabled(false);
                        canselStreaming();
                    }
                });
            }
        };

        // Adiciona o callback ao Dispatcher da Activity
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);
    }

    private void canselStreaming() {
        DialogUtil.showDialog(requireContext(),
                getString(R.string.title_cansel_streaming),
                getString(R.string.msg_cansel_streaming), new DialogUtil.DialogCallback() {
            @Override
            public void onConfirm() {
                DLNAManager.stop(deviceUrl);
                NavHostFragment.findNavController(ControloFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });
    }

    private void updateVolume() {
        String targetUrl = (renderingControlUrl != null) ? renderingControlUrl : deviceUrl;
        String args = "<Channel>Master</Channel><DesiredVolume>" + currentVolume + "</DesiredVolume>";
        DLNAManager.sendRenderingCommand(targetUrl, "SetVolume", args);
        Toast.makeText(requireContext(), "Volume: " + currentVolume + "%", Toast.LENGTH_SHORT).show();
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void startProgressUpdater() {
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying && binding.videoSeekBar.getMax() > 0) {
                    int current = binding.videoSeekBar.getProgress();
                    if (current < binding.videoSeekBar.getMax()) {
                        binding.videoSeekBar.setProgress(current + 1);
                        binding.txt.setText("Time: " + formatDuration((current + 1) * 1000L));
                    }
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateProgressRunnable);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && updateProgressRunnable != null) {
            handler.removeCallbacks(updateProgressRunnable);
        }
        binding = null;
    }
}