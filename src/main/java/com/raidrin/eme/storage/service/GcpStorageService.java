package com.raidrin.eme.storage.service;

import com.google.cloud.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Google Cloud Storage
 */
@Service
@RequiredArgsConstructor
public class GcpStorageService {

    @Value("${gcp.storage.bucket-name:eme-flashcard-images}")
    private String bucketName;

    /**
     * Upload a file to GCP Cloud Storage
     *
     * @param fileName Name of the file in the bucket
     * @param content  File content as byte array
     * @param contentType MIME type of the file (e.g., "image/jpeg")
     * @return Public URL of the uploaded file
     */
    public String uploadFile(String fileName, byte[] content, String contentType) {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            System.out.println("Uploading file to GCS: " + fileName);
            Blob blob = storage.create(blobInfo, content);

            System.out.println("File uploaded successfully: " + blob.getName());
            return String.format("gs://%s/%s", bucketName, fileName);

        } catch (Exception e) {
            System.err.println("Failed to upload file to GCS: " + e.getMessage());
            throw new RuntimeException("Failed to upload file to GCS: " + fileName, e);
        }
    }

    /**
     * Download a file from a URL and upload it to GCP Cloud Storage
     *
     * @param imageUrl URL of the image to download
     * @param fileName Destination file name in the bucket
     * @return GCS URL of the uploaded file
     */
    public String downloadAndUpload(String imageUrl, String fileName) {
        try {
            System.out.println("Downloading image from: " + imageUrl);

            // Download the image
            URL url = new URL(imageUrl);
            byte[] imageBytes;

            try (InputStream in = url.openStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                imageBytes = out.toByteArray();
            }

            System.out.println("Downloaded " + imageBytes.length + " bytes");

            // Determine content type from file extension
            String contentType = getContentType(fileName);

            // Upload to GCS
            return uploadFile(fileName, imageBytes, contentType);

        } catch (IOException e) {
            System.err.println("Failed to download and upload file: " + e.getMessage());
            throw new RuntimeException("Failed to download and upload file from URL: " + imageUrl, e);
        }
    }

    /**
     * Download a file from GCP Cloud Storage
     *
     * @param fileName Name of the file in the bucket
     * @return File content as byte array
     */
    public byte[] downloadFile(String fileName) {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            BlobId blobId = BlobId.of(bucketName, fileName);
            Blob blob = storage.get(blobId);

            if (blob == null) {
                throw new RuntimeException("File not found in GCS: " + fileName);
            }

            return blob.getContent();

        } catch (Exception e) {
            System.err.println("Failed to download file from GCS: " + e.getMessage());
            throw new RuntimeException("Failed to download file from GCS: " + fileName, e);
        }
    }

    /**
     * Delete a file from GCP Cloud Storage
     *
     * @param fileName Name of the file in the bucket
     * @return true if deleted, false otherwise
     */
    public boolean deleteFile(String fileName) {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            BlobId blobId = BlobId.of(bucketName, fileName);
            return storage.delete(blobId);

        } catch (Exception e) {
            System.err.println("Failed to delete file from GCS: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a file exists in GCP Cloud Storage
     *
     * @param fileName Name of the file in the bucket
     * @return true if exists, false otherwise
     */
    public boolean fileExists(String fileName) {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            BlobId blobId = BlobId.of(bucketName, fileName);
            Blob blob = storage.get(blobId);
            return blob != null && blob.exists();

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get a signed URL for temporary access to a file
     *
     * @param fileName Name of the file in the bucket
     * @param durationMinutes Duration in minutes for URL validity
     * @return Signed URL as string
     */
    public String getSignedUrl(String fileName, int durationMinutes) {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            BlobId blobId = BlobId.of(bucketName, fileName);
            Blob blob = storage.get(blobId);

            if (blob == null) {
                throw new RuntimeException("File not found in GCS: " + fileName);
            }

            URL signedUrl = blob.signUrl(durationMinutes, TimeUnit.MINUTES);
            return signedUrl.toString();

        } catch (Exception e) {
            System.err.println("Failed to generate signed URL: " + e.getMessage());
            throw new RuntimeException("Failed to generate signed URL for: " + fileName, e);
        }
    }

    private String getContentType(String fileName) {
        String lowerCase = fileName.toLowerCase();
        if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerCase.endsWith(".png")) {
            return "image/png";
        } else if (lowerCase.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerCase.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerCase.endsWith(".mp3")) {
            return "audio/mpeg";
        } else {
            return "application/octet-stream";
        }
    }
}
