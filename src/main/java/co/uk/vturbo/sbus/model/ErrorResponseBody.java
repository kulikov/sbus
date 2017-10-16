package co.uk.vturbo.sbus.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseBody {

    private String message;

    private String error;

    @JsonProperty("_links")
    private Map<String, Object> links;
}
