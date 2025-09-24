package uk.gov.justice.laa.dstew.access.specification;

import org.springframework.data.jpa.domain.Specification;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

class ApplicationSpecification {

    static Specification<ApplicationEntity> Build()
    {
        return isPending().and(isEmergencyCase());
    }

    static Specification<ApplicationEntity> isPending(){
        return (root, query, builder) -> {            
            return builder.equal(root.get("statusCode"), "Pending");
        };
    }

    static Specification<ApplicationEntity> isEmergencyCase(){
        return (root, query, builder) -> {
            return builder.equal(root.get("somethingSomething"), false);
        };
    }
}
