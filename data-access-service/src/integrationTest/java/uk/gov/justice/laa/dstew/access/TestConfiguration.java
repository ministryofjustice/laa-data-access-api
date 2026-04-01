package uk.gov.justice.laa.dstew.access;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationMakeDecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.LinkedApplicationEntityFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerAssignFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerUnassignFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.decision.DecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.decision.MeritsDecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.domainevents.DomainEventFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingFactoryImpl;

@Configuration
public class TestConfiguration {

}