package com.rogger.xcast10.gallery;


import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.rogger.xcast10.R;
import com.rogger.xcast10.databinding.FragmentGalleryBinding;
import com.rogger.xcast10.gallery.data.DataVideo;
import com.rogger.xcast10.gallery.data.VideoItem;
import com.rogger.xcast10.service.Streaming;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class GalleryFragment extends Fragment {
    private FragmentGalleryBinding binding;
    private LruCache<String, Bitmap> cache;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                           // loadVideoAsync();
                        }
                    }
            );


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initCache();
        binding.rcGallery.setLayoutManager(new LinearLayoutManager(getContext()));
/*
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
        ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        } else {

        }

 */
        loadVideoAsync();
    }

    private void initCache() {
        int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024) / 8;
        cache = new LruCache<>(cacheSize);
    }

    private void loadVideoAsync() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            List<VideoItem> item = DataVideo.getVideoList(requireContext());
            handler.post(() -> {

                GalleryAdapter adapter = new GalleryAdapter(
                        requireContext(),
                        item,
                        this::onImageSelected,
                        cache
                );
                binding.rcGallery.setAdapter(adapter);
                binding.progressLoading.setVisibility(View.GONE);
            });

        });
    }
    private void onImageSelected(VideoItem itens) {

        Streaming.startStreaming(requireContext(), itens, new Streaming.StreamingCallback() {
            @Override
            public void onLoading(boolean loading) {
                binding.progressLoading.setVisibility(
                        loading ? View.VISIBLE : View.GONE
                );
            }

            @Override
            public void onSuccess(VideoItem item, String deviceUrl, String renderingControlUrl) {
                Bundle bundle = new Bundle();
                bundle.putString("deviceUrl", deviceUrl);
                bundle.putString("renderingControlUrl", renderingControlUrl);
                bundle.putString("videoTitle", item.getTitle());
                bundle.putLong("durationMs", item.getDuration());

                NavHostFragment.findNavController(GalleryFragment.this)
                        .navigate(R.id.action_nav_gallery_fragment_to_SecondFragment, bundle);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}