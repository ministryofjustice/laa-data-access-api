# Guide: Bringing an Entity into the Aggregate Boundary

This guide describes how to move an existing entity that holds a bare `UUID applicationId` column into the `ApplicationEntity` aggregate root. It uses `CertificateEntity` as the worked example, and is intended to be reused for any future entity that follows the same pattern.

---

## When to use this guide

An entity is a candidate for this treatment when:

- It has a `@Column UUID applicationId` field that is a FK to `applications.id`
- It is fetched and written exclusively in the context of a single application
- It should be created, updated, or deleted as part of the application lifecycle

---

## Overview of the pattern

| Concern | Before | After |
|---|---|---|
| FK owner | `@Column UUID applicationId` in child | `@OneToOne/@ManyToOne @JoinColumn` in child |
| Parent side | nothing (or separate `@OneToMany`) | `@OneToOne(mappedBy, cascade, orphanRemoval)` |
| Write path | direct `childRepository.save(entity)` | set on parent, let `applicationRepository.save()` cascade |
| Delete path | explicit `childRepository.delete(entity)` | `application.setChild(null)`, cascade via `orphanRemoval` |
| DB FK | `ON DELETE CASCADE` | plain FK (no cascade) — JPA owns deletion |

---

## Step 1 — Assess the DB FK

Check the migration that created the child table. If the FK has `ON DELETE CASCADE`:

```sql
CONSTRAINT fk_child_applications_id FOREIGN KEY (application_id)
    REFERENCES applications(id) ON DELETE CASCADE
```

You **must** remove the cascade before enabling `orphanRemoval = true` in JPA, otherwise deleting an application causes a conflict: the DB cascade deletes the child row, then Hibernate tries to do so again and throws `StaleStateException`.

Create a new migration (`V<next>__remove_cascade_delete_from_<table>.sql`):

```sql
ALTER TABLE <child_table> DROP CONSTRAINT <constraint_name>;

ALTER TABLE <child_table>
    ADD CONSTRAINT <constraint_name>
        FOREIGN KEY (application_id) REFERENCES applications (id);
```

Find the real constraint name in the original migration file — do not guess it.

---

## Step 2 — Update the child entity

Replace the bare `@Column UUID applicationId` with a proper JPA relationship:

```java
// Before
@Column(name = "application_id", nullable = false)
private UUID applicationId;

// After
@OneToOne(fetch = FetchType.LAZY)       // or @ManyToOne for a collection relationship
@JoinColumn(name = "application_id", nullable = false, unique = true)
private ApplicationEntity application;
```

Add the necessary imports: `OneToOne`/`ManyToOne`, `FetchType`, `JoinColumn`.

Remove any import of `UUID` that was only used for `applicationId`.

---

## Step 3 — Add the relationship to `ApplicationEntity`

Add the inverse side of the relationship, with cascade and orphanRemoval:

```java
// @OneToOne example (certificate)
@OneToOne(
    mappedBy = "application",
    cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
    orphanRemoval = true,
    fetch = FetchType.LAZY)
private ChildEntity child;

// @OneToMany example (collection)
@OneToMany(
    mappedBy = "application",
    cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
    orphanRemoval = true,
    fetch = FetchType.LAZY)
@Builder.Default
private Set<ChildEntity> children = new HashSet<>();
```

**Why `REMOVE` is safe here**: the DB `ON DELETE CASCADE` was removed in Step 1, so Hibernate is now the sole owner of delete behaviour. `orphanRemoval = true` lets you remove a child simply by nulling (or removing from the collection) and saving the parent.

**Cascade strategy for other child collections that still have DB `ON DELETE CASCADE`** (e.g. `meritsDecisions`): use `{PERSIST, MERGE}` only, no `REMOVE`, no `orphanRemoval`. The DB cascade handles deletion and Hibernate must not try to duplicate it.

---

## Step 4 — Update the entity graph

`makeDecision` loads the application via `findByIdWithDecisionGraph`. Add the new relationship to the `attributePaths`:

```java
@EntityGraph(attributePaths = {
    "decision", "decision.meritsDecisions", "proceedings", "certificate"
    // add your new attribute name here
})
```

Without this, the first access to the lazy field inside the service transaction will either throw `LazyInitializationException` or issue an N+1 query.

---

## Step 5 — Rename repository methods

Spring Data derived-query methods that previously navigated `applicationId` directly must be updated to use the FK traversal syntax:

```java
// Before
Optional<ChildEntity> findByApplicationId(UUID applicationId);
boolean existsByApplicationId(UUID applicationId);
void deleteByApplicationId(UUID applicationId);

// After
Optional<ChildEntity> findByApplication_Id(UUID applicationId);
boolean existsByApplication_Id(UUID applicationId);
void deleteByApplication_Id(UUID applicationId);
```

The underscore tells Spring Data to traverse the `application` association to reach `id`, rather than looking for a field named `applicationId`.

---

## Step 6 — Rewrite the service write path

### GRANTED / create path

Instead of calling `childRepository.save(child)`, wire the child through the parent:

```java
ChildEntity child = application.getChild() != null
    ? application.getChild()           // update existing
    : ChildEntity.builder().build();   // create new

child.setApplication(application);
child.setContent(request.getContent());
application.setChild(child);
// no childRepository.save() — applicationRepository.save(application) cascades
```

### REFUSED / delete path

```java
// Before (explicit delete)
childRepository.delete(application.getChild());
application.setChild(null);

// After (orphanRemoval handles it)
application.setChild(null);
// applicationRepository.save(application) causes Hibernate to DELETE the orphan
```

If the service no longer calls `childRepository` at all, remove it from the constructor and the field.

---

## Step 7 — Update `CertificateEntityGenerator` (or equivalent)

In `createDefault()`, replace `.applicationId(UUID.randomUUID())` with nothing (no applicationId) — or, if the generator is always called with a specific application, pass `.application(applicationEntity)` via the customiser at the call site.

---

## Step 8 — Fix unit tests

### Stubs that used `childRepository.findByApplicationId`

The service no longer calls `childRepository.findByApplicationId`. Instead, the entity is loaded via `application.getChild()` from the entity graph. Pre-populate the application entity:

```java
// Before
when(childRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existingChild));

// After
applicationEntity.setChild(existingChild);
// (applicationEntity is already the return value of the findByIdWithDecisionGraph stub)
```

### Assertions on saved child

The service no longer calls `childRepository.save()`. Capture via the application:

```java
// Before
verify(childRepository, times(1)).save(childCaptor.capture());
ChildEntity saved = childCaptor.getValue();
assertThat(saved.getApplicationId()).isEqualTo(applicationId);

// After
ArgumentCaptor<ApplicationEntity> appCaptor = ArgumentCaptor.forClass(ApplicationEntity.class);
verify(applicationRepository, times(1)).save(appCaptor.capture());
ChildEntity saved = appCaptor.getValue().getChild();
assertThat(saved).isNotNull();
assertThat(saved.getApplication()).isNotNull();
assertThat(saved.getContent()).isEqualTo(expectedContent);
```

### Delete verification (REFUSED/delete path)

```java
// Before
when(childRepository.existsByApplicationId(applicationId)).thenReturn(certificateExists);
verify(childRepository, times(certificateExists ? 1 : 0)).delete(any());

// After — no stub needed; pre-set or don't set the child on the entity
if (childShouldExist) {
    applicationEntity.setChild(existingChild);
}
// No verify on childRepository — the service no longer uses it
```

### Remove stale `@MockitoBean`

If the `childRepository` is no longer injected in the service, the `@MockitoBean` in `BaseServiceTest` is only needed if another service test still uses it. If not, remove it.

---

## Step 9 — Fix integration tests

### `createAndPersist` builder calls

Replace `.applicationId(applicationEntity.getId())` with `.application(applicationEntity)`:

```java
// Before
persistedDataGenerator.createAndPersist(
    ChildEntityGenerator.class,
    builder -> builder.applicationId(applicationEntity.getId()));

// After
persistedDataGenerator.createAndPersist(
    ChildEntityGenerator.class,
    builder -> builder.application(applicationEntity));
```

The application entity passed here must already be persisted (returned from a previous `createAndPersist` call) so that Hibernate has a managed reference to attach.

### Assertions on the child's application FK

`child.getApplicationId()` no longer exists. Use a repository method instead:

```java
// Before
assertThat(child.getApplicationId()).isEqualTo(applicationEntity.getId());

// After
assertThat(childRepository.existsByApplication_Id(applicationEntity.getId())).isTrue();
```

### `CertificateRepositoryTest` (or equivalent)

Add `"application"` (or the name of your traversed field) to the `ignoringFields` list in the equality assertion to avoid lazy-proxy comparison issues:

```java
assertThat(expected)
    .usingRecursiveComparison()
    .ignoringFields("createdAt", "modifiedAt", "application")
    .isEqualTo(actual);
```

### `PersistedDataGenerator` teardown

Application deletion must now go through JDBC rather than JPA, because the lazy `application` back-reference on the child entity becomes a detached proxy after the session closes. When Hibernate attempts to load the application entity for deletion it triggers the cascade.MERGE path on the child, which fails on the detached proxy.

```java
// Before
trackedApplicationIds.forEach(id -> appRepo.findById(id).ifPresent(appRepo::delete));

// After
trackedApplicationIds.forEach(id ->
    jdbcTemplate.update("DELETE FROM applications WHERE id = ?", id));
```

Since the DB FK no longer has `ON DELETE CASCADE`, you must ensure the child rows are deleted before the application row. The cleanest way is to add JDBC pre-deletion for the child table:

```java
trackedApplicationIds.forEach(id ->
    jdbcTemplate.update("DELETE FROM <child_table> WHERE application_id = ?", id));
trackedApplicationIds.forEach(id ->
    jdbcTemplate.update("DELETE FROM applications WHERE id = ?", id));
```

---

## Checklist

- [ ] Migration created: `ON DELETE CASCADE` removed from child table FK
- [ ] Child entity: `UUID applicationId` → `@OneToOne/@ManyToOne ApplicationEntity application`
- [ ] `ApplicationEntity`: inverse side added with `cascade`, `orphanRemoval`, `fetch = LAZY`
- [ ] `ApplicationRepository` entity graph updated to include the new attribute
- [ ] Child repository methods renamed to `findByApplication_Id` etc.
- [ ] `CertificateService` (or equivalent) updated for renamed method
- [ ] `ApplicationService` write path uses aggregate; `childRepository` removed if unused
- [ ] `ChildEntityGenerator.createDefault()` no longer sets `applicationId`
- [ ] Unit tests: stubs replaced with entity pre-population; `childRepository.save` verifies replaced with app captor
- [ ] Integration tests: builder sites use `.application(entity)`; `getApplicationId()` assertions replaced
- [ ] `PersistedDataGenerator` teardown uses JDBC for application (and child if needed)
- [ ] Full build green: `./gradlew spotlessApply && ./gradlew build`
