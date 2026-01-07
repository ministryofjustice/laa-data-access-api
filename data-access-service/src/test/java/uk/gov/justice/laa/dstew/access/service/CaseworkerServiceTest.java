package uk.gov.justice.laa.dstew.access.service;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.mapper.CaseworkerMapper;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;

@ExtendWith(MockitoExtension.class)
public class CaseworkerServiceTest {
    @InjectMocks
    private CaseworkerService caseworkerService;

    @Mock
    private CaseworkerRepository caseworkerRepository;
    @Mock
    private CaseworkerMapper caseworkerMapper;


}
