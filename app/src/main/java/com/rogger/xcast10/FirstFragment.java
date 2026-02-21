package com.rogger.xcast10;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentFirstBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Primeiro fragmento da aplicação, responsável pela listagem e seleção de dispositivos
 * ou vídeos para iniciar a transmissão. Gere a descoberta de dispositivos e o início do streaming.
 */
public class FirstFragment extends Fragment {
    private final List<DLNADevice> devices = new ArrayList<>();
    private LocalHttpServer httpServer;

    private FragmentFirstBinding binding;

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

        checkPermissions();

        // Verificar se já existe um dispositivo selecionado anteriormente
        DLNADevice savedDevice = DLNAManager.getSelectedDevice();
        if (savedDevice != null) {
            binding.tvStatus.setText("Conectado a: " + savedDevice.getName());
            binding.btnSelectVideo.setEnabled(true);
        }

        binding.btnFindDevices.setOnClickListener(v -> startDiscovery());
        binding.btnSelectVideo.setOnClickListener(v -> pickVideo());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_MEDIA_VIDEO}, 1);
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private void startDiscovery() {
        devices.clear();
        binding.progressBar.setVisibility(View.VISIBLE);

        DLNAManager.discoverDevices(requireContext(), new DLNAManager.DiscoveryCallback() {

            @Override
            public void onDeviceFound(DLNADevice device) {
                binding.getRoot().post(() -> {
                    for (DLNADevice d : devices)
                        if (d.getServiceUrl().equals(device.getServiceUrl()))
                            return;
                    devices.add(device);
                    Log.d("Devais", device.getName());
                });
            }

            @Override
            public void onFinished(String msg) {
                binding.getRoot().post(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showDeviceDialog(msg); // agora abre só 1 vez
                });
            }
        });
    }

    private void showDeviceDialog(String msg) {
        binding.progressBar.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        if (devices.isEmpty()) {
            builder.setTitle(msg);
            builder.setMessage("Verifique se a TV está na mesma rede \uD83D\uDCE1 ou reinicie a sua TV \uD83D\uDD04.");
        } else {
            builder.setTitle("Selecione a Smart TV");
            ArrayAdapter<DLNADevice> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, devices);
            builder.setAdapter(adapter, (dialog, which) -> {
                DLNADevice selected = devices.get(which);
                DLNAManager.setSelectedDevice(selected); // Salva o dispositivo no DLNAManager
                binding.tvStatus.setText("Conectado a: " + selected.getName());
                binding.btnSelectVideo.setEnabled(true);

            });
        }
        builder.show();
    }

    private final ActivityResultLauncher<String> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    startStreaming(uri);
                }
            }
    );

    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }

    private void startStreaming(Uri videoUri) {
        DLNADevice selectedDevice = DLNAManager.getSelectedDevice();
        if (selectedDevice == null) {
            Toast.makeText(requireContext(), "Por favor, selecione um dispositivo primeiro", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        // Iniciar servidor local
        try {
            if (httpServer != null) httpServer.stop();
            httpServer = new LocalHttpServer(requireContext(), 8080);
            httpServer.setVideoUri(videoUri);
            httpServer.start();

            // Pegar IP do celular para a URL
            String ip = DLNAManager.getLocalIpAddress();
            String videoUrl = "http://" + ip + ":8080/video.mp4";
            Log.d("CastApp", "Servindo vídeo em: " + videoUrl);

            DLNAManager.setAVTransportURI(selectedDevice.getServiceUrl(), videoUrl);
            // Enviar comando para a TV carregar o vídeo

            // Simular tempo de carregamento e abrir tela de controle
            binding.btnFindDevices.postDelayed(() -> {
                DLNAManager.play(selectedDevice.getServiceUrl());
                binding.progressBar.setVisibility(View.GONE);

                Bundle bundle = new Bundle();
                bundle.putString("deviceUrl", selectedDevice.getServiceUrl());
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment, bundle);

            }, 2000);

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Erro ao iniciar transmissão", Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
