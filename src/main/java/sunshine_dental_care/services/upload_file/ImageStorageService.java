package sunshine_dental_care.services.upload_file;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    ImageUploadResult upload(MultipartFile file, String folder) throws Exception;
    void delete(String publicId) throws Exception;

    class ImageUploadResult {
        private final String url;
        private final String publicId;

        public ImageUploadResult(String url, String publicId) {
            this.url = url;
            this.publicId = publicId;
        }

        public String getUrl() {
            return url;
        }

        public String getPublicId() {
            return publicId;
        }
    }
}


