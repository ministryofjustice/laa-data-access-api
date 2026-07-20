package uk.gov.justice.laa.dstew.access.command.application.data;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationPiiRepository.ExistingFragment;

/** In-memory {@link ApplicationPiiRepository} used by default and test profiles. */
@Component
@Primary
@Profile("test")
public class MockApplicationPiiRepository implements ApplicationPiiRepository {

  private final ConcurrentHashMap<UUID, FragmentEntry> fragments = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<RedactionAudit> redactionAudits = new CopyOnWriteArrayList<>();

  @Override
  public void saveFragment(
      UUID applicationId,
      UUID fragmentRef,
      String sectionName,
      byte[] content,
      Instant savedAt) {
    fragments.putIfAbsent(
        fragmentRef,
        new FragmentEntry(
            applicationId, sectionName, contentHash(content), content, savedAt, PiiStatus.PRESENT));
  }

  @Override
  public Optional<ExistingFragment> findLatestFragment(UUID applicationId, String sectionName) {
    return fragments.entrySet().stream()
        .filter(
            e ->
                e.getValue().applicationId().equals(applicationId)
                    && e.getValue().sectionName().equals(sectionName)
                    && e.getValue().status() == PiiStatus.PRESENT)
        .max(Comparator.comparing(e -> e.getValue().savedAt()))
        .map(e -> new ExistingFragment(e.getKey(), e.getValue().contentHash()));
  }

  @Override
  public Map<UUID, byte[]> findFragments(Set<UUID> fragmentRefs) {
    Map<UUID, byte[]> result = new HashMap<>();
    for (UUID ref : fragmentRefs) {
      FragmentEntry entry = fragments.get(ref);
      if (entry != null && entry.status() == PiiStatus.PRESENT) {
        result.put(ref, entry.content());
      }
    }
    return result;
  }

  @Override
  public void redactAllForApplication(UUID applicationId, String reason, String actor) {
    fragments.replaceAll(
        (ref, entry) ->
            entry.applicationId().equals(applicationId)
                ? new FragmentEntry(
                    entry.applicationId(),
                    entry.sectionName(),
                    entry.contentHash(),
                    entry.content(),
                    entry.savedAt(),
                    PiiStatus.REDACTED)
                : entry);
    redactionAudits.add(new RedactionAudit(applicationId, reason, actor));
  }

  private String contentHash(byte[] content) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private record FragmentEntry(
      UUID applicationId,
      String sectionName,
      String contentHash,
      byte[] content,
      Instant savedAt,
      PiiStatus status) {}

  private record RedactionAudit(UUID applicationId, String reason, String actor) {}
}
