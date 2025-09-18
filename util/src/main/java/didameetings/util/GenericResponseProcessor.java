package didameetings.util;

import java.util.List;

public abstract class GenericResponseProcessor<T> {

    public abstract boolean onNext(List<T> allResponses, T lastResponse);
}
