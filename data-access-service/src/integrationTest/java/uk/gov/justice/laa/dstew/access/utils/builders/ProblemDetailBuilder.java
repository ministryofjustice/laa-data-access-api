package uk.gov.justice.laa.dstew.access.utils.builders;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class ProblemDetailBuilder {
    private HttpStatus status;
    private String detail;
    private String title;

    public static ProblemDetailBuilder create() {
        return new ProblemDetailBuilder();
    }

    public ProblemDetailBuilder status(HttpStatus status) {
        this.status = status;
        return this;
    }

    public ProblemDetailBuilder detail(String detail) {
        this.detail = detail;
        return this;
    }

    public ProblemDetailBuilder title(String title) {
        this.title = title;
        return this;
    }

    public ProblemDetail build() {
        ProblemDetail detail = ProblemDetail.forStatus(this.status);
        detail.setTitle(this.title);
        detail.setDetail(this.detail);
        return detail;
    }
}