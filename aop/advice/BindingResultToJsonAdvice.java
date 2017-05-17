package advice;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.util.ArrayList;


@Aspect
@Component
public class BindingResultToJsonAdvice {
    private MessageSource messageSource;

    public MessageSource getMessageSource() {
        return messageSource;
    }

    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Around("@annotation(org.springframework.web.bind.annotation.ResponseBody) && execution(* *(@javax.validation.Valid (*),..))")
    public Object toJson(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof BeanPropertyBindingResult) {
            ErrorMessageResponse response = new ErrorMessageResponse();
            BeanPropertyBindingResult bindingResult = (BeanPropertyBindingResult) result;
            for (FieldError error : bindingResult.getFieldErrors()) {
                response.add(error.getField(), messageSource.getMessage(error, LocaleContextHolder.getLocale()));
            }
            return response;
        }
        return result;
    }


    class ErrorMessageResponse extends ArrayList<ErrorMessageResponse.Value> {

        ErrorMessageResponse() {
        }

        void add(String field, String message) {
            this.add(new Value(field, message));
        }


        class Value {
            private String field;
            private String text;

            Value(String field, String text) {
                this.field = field;
                this.text = text;
            }

            public String getText() {
                return text;
            }

            public String getField() {
                return field;
            }

            @Override
            public String toString() {
                return "ErrorMessage [field=" + field + ", text=" + text + "]";
            }

        }

    }
}
