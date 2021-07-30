import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExtractRegion {
	public static void main(String[] args) {
		test();
	}

	public static void test() {
		log.info("---START---");
		String fileName = "C:\\Users\\baamjuri\\Downloads\\Salvage7.pdf";
		try (PDDocument doc = PDDocument.load(new File(fileName))) {
			if (!doc.isEncrypted()) {
				PDPage page = doc.getPage(0);

				PDFTextStripperByArea stripper = new PDFTextStripperByArea();
				Rectangle2D region = new Rectangle2D.Double(28, 243, 525, 48.5);
				String regionName = "region1";
				stripper.setSortByPosition( true );
				stripper.setStartPage( 0 );
	            stripper.setEndPage( doc.getNumberOfPages() );
				stripper.addRegion(regionName, region);
				stripper.extractRegions(page);
				String text = stripper.getTextForRegion(regionName);
				log.info("Extracted text is: {}", text);
			}
		} catch (IOException e) {
			log.error("Error while loading the PDF");
		}
		log.info("---END---");
	}
}
