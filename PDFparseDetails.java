import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PDFparseDetails{
	private List<PDFPageDetails> pdfPageDetailsList;
	
	@Setter
	@Getter
	@ToString
	public static class PDFPageDetails{
		private int pageNumber;
		private List<RegionDetails> regionDetailsList;
	}
	
	@Setter
	@Getter
	@ToString
	public static class RegionDetails{
		private String name;
		private CoOrdinates coOrdinates;
		private Character type; 
		private Map<String, String> mappings;
	}
	
	@Setter
	@Getter
	@ToString
	public static class CoOrdinates{
		private double x;
		private double y;
		private double w;
		private double h;
	}
}
