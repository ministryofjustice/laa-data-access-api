package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LinkApplicationUseCaseTest {

  @Mock private LinkApplicationCommandHandler commandHandler;

  @InjectMocks private LinkApplicationUseCase useCase;

  @Test
  void executeDelegatesExactlyOnceToTheCommandHandler() {
    var command =
        new LinkApplicationCommand(
            UUID.randomUUID(), UUID.randomUUID(), LinkType.FAMILY, Instant.now());

    useCase.execute(command);

    verify(commandHandler).handle(command);
    verifyNoMoreInteractions(commandHandler);
  }

  @Test
  void useCaseRemainsAThinNonTransactionalDelegate() throws NoSuchMethodException {
    assertThat(LinkApplicationUseCase.class.isAnnotationPresent(Transactional.class)).isFalse();
    assertThat(
            LinkApplicationUseCase.class
                .getDeclaredMethod("execute", LinkApplicationCommand.class)
                .isAnnotationPresent(Transactional.class))
        .isFalse();
    assertThat(LinkApplicationUseCase.class.getDeclaredConstructors()).hasSize(1);
    assertThat(LinkApplicationUseCase.class.getDeclaredConstructors()[0].getParameterTypes())
        .containsExactly(LinkApplicationCommandHandler.class);
    assertThat(
            Arrays.stream(LinkApplicationUseCase.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType().getSimpleName())
                .toList())
        .containsExactly("LinkApplicationCommandHandler");
  }
}
