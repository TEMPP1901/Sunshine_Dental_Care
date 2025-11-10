package sunshine_dental_care.services.auth_service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Service
public class CloudinaryUploadService {
    private final Cloudinary cloudinary;
    @Value("${cloudinary.folder_root:dental-clinic}") private String folderRoot;

    public CloudinaryUploadService(Cloudinary cloudinary) { this.cloudinary = cloudinary; }

    public record UploadResult(String url, String publicId) {}

    public UploadResult upload(MultipartFile file, String subFolder) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty");
        String folder = buildFolderPath(subFolder);
        Map<?, ?> res = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image",
                        "use_filename", true,
                        "unique_filename", true,
                        "overwrite", false
                )
        );
        return new UploadResult((String) res.get("secure_url"), (String) res.get("public_id"));
    }

    public void deleteByPublicId(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) return;
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
    }

    private String buildFolderPath(String subFolder) {
        if (subFolder == null || subFolder.isBlank()) return folderRoot;
        if (subFolder.startsWith("/")) subFolder = subFolder.substring(1);
        return folderRoot + "/" + subFolder;
    }
}

