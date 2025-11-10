package sunshine_dental_care.api.locale;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocaleController {
    @GetMapping("/locale")
    public void setLocale(String lang, HttpServletResponse res){
        res.setStatus(204);
    }
}
