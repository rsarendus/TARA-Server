package ee.ria.sso.config.idcard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty("id-card.enabled")
@Configuration
@Slf4j
public class IDCardConfiguration {

    @Autowired
    private IDCardConfigurationProvider idcardConfigurationProvider;

    // TODO: this file may not be necessary

}
