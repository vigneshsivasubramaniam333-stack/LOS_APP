import java.time.format.*;
import java.time.*;
import java.lang.*;

StringBuilder builder = new StringBuilder();
String newLine = System.getProperty("line.separator");
result = "";
if (bankRepository != null) {
    LocalDate currentDate  = bankRepository.findBank().getCurrentWorkingDate();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    String reporingDate = (currentDate != null) ? formatter.format(currentDate) : null;
    header = "HD|NB77920001||"+reporingDate+"|"+reporingDate+"|01||";
    builder.append(header).append(newLine);
} else if (footer != null) {
    footer = "TS|"+size+"|"+size+"||";
    builder.append(footer);
} else {
	for (int i=0; i<results.size(); i++) {
            String[] hh = results.getItems().get(i);
            result += hh[0];
    } 
    builder.append(result);
}
result = builder.toString();