// xử lí config i18n

package sunshine_dental_care.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class i18nConfig implements WebMvcConfigurer {
    @Bean
    public LocaleResolver localeResolver(){
        CookieLocaleResolver clr = new CookieLocaleResolver();
        clr.setCookieName("lang");
        clr.setDefaultLocale(Locale.ENGLISH);
        clr.setCookieMaxAge((int) Duration.ofDays(365).getSeconds());
        clr.setCookiePath("/");
        return clr;
    }

    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang"); // /locale?lang=vi
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
