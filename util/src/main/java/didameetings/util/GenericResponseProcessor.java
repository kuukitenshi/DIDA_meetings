package didameetings.util;

import java.util.List;

public abstract class GenericResponseProcessor<T> {

    abstract boolean onNext(List<T> allResponses, T lastResponse);
}
