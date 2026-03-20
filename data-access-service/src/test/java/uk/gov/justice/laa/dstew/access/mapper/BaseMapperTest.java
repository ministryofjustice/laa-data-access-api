package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import uk.gov.justice.laa.dstew.access.utils.helpers.SpringContext;

@JsonTest
@Import(SpringContext.class)
public abstract class BaseMapperTest {
    @Autowired
    protected ObjectMapper objectMapper;
}

