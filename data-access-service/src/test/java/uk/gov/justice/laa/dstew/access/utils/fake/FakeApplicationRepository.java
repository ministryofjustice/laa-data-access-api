package uk.gov.justice.laa.dstew.access.utils.fake;

import org.mockito.Mockito;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class FakeApplicationRepository {

    private ApplicationRepository fakeApplicationRepository;

    public FakeApplicationRepository create() {
        this.fakeApplicationRepository = Mockito.mock(ApplicationRepository.class);
        return this;
    }

    public FakeApplicationRepository returnsForFindAll(List<ApplicationEntity> applications) {
        when(this.fakeApplicationRepository.findAll()).thenReturn(applications);
        return this;
    }

    public FakeApplicationRepository returnsForFindById(ApplicationEntity entity) {
        when(this.fakeApplicationRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        return this;
    }

    public <T extends Throwable> FakeApplicationRepository throwsForFindAll(Class<T> throwable) {
        when(this.fakeApplicationRepository.findAll()).thenThrow(throwable);
        return this;
    }

    public ApplicationRepository build() {
        return this.fakeApplicationRepository;
    }
}
