import java.time.format.*;
import java.time.*;
import java.lang.*;

StringBuilder builder = new StringBuilder();
if (bankRepository != null) {
    LocalDate currentDate  = bankRepository.findBank().getCurrentWorkingDate();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    String reporingDate = (currentDate != null) ? formatter.format(currentDate) : null;
    String password = "                              ";
    header = "TUDF12NB77920001                    SAMUNNATIFI     10"+reporingDate+password+"A00000                                                ";
    builder.append(header);
} else if (footer != null) {
    footer = "TRLR";
    builder.append(footer);
} else {
	for (int i=0; i<results.size(); i++) {
            String[] hh = results.getItems().get(i);
            result += hh[0];
    } 
    builder.append(result);
}
result = builder.toString();