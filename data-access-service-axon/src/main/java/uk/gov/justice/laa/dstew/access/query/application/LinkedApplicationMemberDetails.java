package uk.gov.justice.laa.dstew.access.query.application;

/** Required list-index details used to describe another member of a linked application group. */
public record LinkedApplicationMemberDetails(
    String laaReference, String clientFirstName, String clientLastName) {}
