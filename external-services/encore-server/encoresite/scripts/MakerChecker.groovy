import java.time.*

addToMakerChecker = "false";
LocalDate currentWorkingDate = bankRepository.findBank().getCurrentWorkingDate();
Period p = Period.between(valueDate, currentWorkingDate);
if(p.getDays() > 0){
	addToMakerChecker = "true";
}