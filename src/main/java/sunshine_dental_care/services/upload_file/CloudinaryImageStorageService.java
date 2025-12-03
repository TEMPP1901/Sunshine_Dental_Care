package sunshine_dental_care.services.upload_file;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
public class CloudinaryImageStorageService implements ImageStorageService {
    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:sdcare}")
    private String rootFolder;

    @Override
    public ImageUploadResult upload(MultipartFile file, String folder) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String targetFolder = (folder == null || folder.isBlank())
                ? rootFolder
                : rootFolder + "/" + folder;

        Map<?, ?> res = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", targetFolder,
                        "resource_type", "image",
                        "overwrite", true
                )
        );
        String url = (String) res.get("secure_url");
        String publicId = (String) res.get("public_id");
        return new ImageUploadResult(url, publicId);
    }

    @Override
    public void delete(String publicId) throws Exception {
        if (publicId == null || publicId.isBlank()) return;
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
    }
}




