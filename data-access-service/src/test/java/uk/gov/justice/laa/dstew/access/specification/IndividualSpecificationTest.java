package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

class IndividualSpecificationTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void givenApplicationId_whenFilterApplicationId_thenCreatesJoinAndEqualPredicate() {
    // given
    UUID applicationId = UUID.randomUUID();
    Root<IndividualEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    Join join = mock(Join.class);
    Path idPath = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.join("applications")).thenReturn(join);
    when(join.get("id")).thenReturn(idPath);
    when(builder.equal(idPath, applicationId)).thenReturn(predicate);

    // when
    Specification<IndividualEntity> result = IndividualSpecification.filterApplicationId(applicationId);
    Predicate actualPredicate = result.toPredicate(root, query, builder);

    // then
    assertThat(result).isNotNull();
    assertThat(actualPredicate).isEqualTo(predicate);
    verify(root).join("applications");
    verify(join).get("id");
    verify(builder).equal(idPath, applicationId);
  }

  @Test
  void givenNullApplicationId_whenFilterApplicationId_thenReturnsNull() {
    // when
    Specification<IndividualEntity> result = IndividualSpecification.filterApplicationId(null);

    // then
    assertThat(result).isNull();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void givenIndividualType_whenFilterIndividualType_thenCreatesEqualPredicate() {
    // given
    IndividualType individualType = IndividualType.CLIENT;
    Root<IndividualEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    Path typePath = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("type")).thenReturn(typePath);
    when(builder.equal(typePath, individualType)).thenReturn(predicate);

    // when
    Specification<IndividualEntity> result = IndividualSpecification.filterIndividualType(individualType);
    Predicate actualPredicate = result.toPredicate(root, query, builder);

    // then
    assertThat(result).isNotNull();
    assertThat(actualPredicate).isEqualTo(predicate);
    verify(root).get("type");
    verify(builder).equal(typePath, individualType);
  }

  @Test
  void givenNullIndividualType_whenFilterIndividualType_thenReturnsNull() {
    // when
    Specification<IndividualEntity> result = IndividualSpecification.filterIndividualType(null);

    // then
    assertThat(result).isNull();
  }
}
