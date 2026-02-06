package uk.gov.justice.laa.dstew.access.utils.factory.Office;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationOffice;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ApplicationOfficeFactory
  extends BaseFactory<ApplicationOffice, ApplicationOffice.ApplicationOfficeBuilder> {

    public ApplicationOfficeFactory() {
        super(ApplicationOffice::toBuilder, ApplicationOffice.ApplicationOfficeBuilder::build);
    }

    @Override
    public ApplicationOffice createDefault() {
        return ApplicationOffice
                .builder()
                .code("AB123C")
                .additionalOfficeData(Map.of())
                .build();
    }
}
