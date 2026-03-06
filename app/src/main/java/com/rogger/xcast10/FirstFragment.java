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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.rogger.xcast10.databinding.FragmentFirstBinding;
import com.rogger.xcast10.service.LocalHttpServer;
import com.rogger.xcast10.util.SharedPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Primeiro fragmento da aplicação, responsável pela listagem e seleção de dispositivos
 * ou vídeos para iniciar a transmissão. Gere a descoberta de dispositivos e o início do streaming.
 */
public class FirstFragment extends Fragment {
    private final List<DLNADevice> devices = new ArrayList<>();
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

        checkPermissions();
        String filmTitle = SharedPreference.getString(requireContext(), String.valueOf(R.string.key_preference));
        if (filmTitle != null) {
            binding.txtLastFilm.setVisibility(View.VISIBLE);
            binding.txtLastFilm.setText("Último filme visto \n" + filmTitle);
        }
        // Tenta recuperar o dispositivo guardado anteriormente no DLNAManager
        selectedDevice = DLNAManager.getSelectedDevice();
        if (selectedDevice != null) {
            binding.tvStatus.setText("TV Conectada: " + selectedDevice.getName());
            binding.btnSelectVideo.setEnabled(true);
        }

        binding.btnFindDevices.setOnClickListener(v -> startDiscovery());

        binding.btnSelectVideo.setOnClickListener(v -> {
            binding.btnSelectVideo.postDelayed(() -> {
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_firstFragment_to_galerryFragment);
            }, 2000);

        });
        setupGalleryResult();
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

    private void setupGalleryResult() {
        getParentFragmentManager().setFragmentResultListener(
                "gallery_result",
                getViewLifecycleOwner(),
                (key, bundle) -> {
                    try {
                        Uri urii = Uri.parse(bundle.getString("imageUri"));
                        if (urii != null) {
                            Log.d("VidioCelecionado", urii.getPath());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
        );
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
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //binding = null;
    }
}