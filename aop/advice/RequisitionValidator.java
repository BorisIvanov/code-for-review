package validator;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.validation.AbstractPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class RequisitionValidator implements Validator {

    @Override
    public boolean supports(Class<?> aClass) {
        return Requisition.class.equals(aClass);
    }

    private FieldError newFieldError(String field, String code) {
        return new FieldError("requisition", field, null, false, new String[]{code}, null, null);
    }

    @Override
    public void validate(Object o, Errors e) {
        Assert.isInstanceOf(AbstractPropertyBindingResult.class, e, "RequisitionValidator cant work");

        AbstractPropertyBindingResult errors = (AbstractPropertyBindingResult) e;
        Requisition requisition = (Requisition) o;
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "caseNumber", "Requisition.caseNumber.empty");
        if (!EmailValidator.getInstance().isValid(requisition.getUserEmail())) {
            errors.addError(newFieldError("userEmail", "Requisition.userEmail.novalid"));
        }
    }

}
