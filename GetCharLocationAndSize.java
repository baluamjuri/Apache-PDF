import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
* This is an example on how to get the x/y coordinates and size of each character in PDF
*/
public class GetCharLocationAndSize extends PDFTextStripper {
	
	static List<String> words = new ArrayList<String>();
  
    public GetCharLocationAndSize() throws IOException {
    }
  
    /**
     * @throws IOException If there is an error parsing the document.
     */	
    public static void main( String[] args ) throws IOException {
        PDDocument document = null;
        String fileName = "C:\\Users\\baamjuri\\Downloads\\Pdf parsing for assignment samples\\Salvage 1.pdf";
        try {
            document = PDDocument.load( new File(fileName) );
            PDFTextStripper stripper = new GetCharLocationAndSize();
            stripper.setSortByPosition( true );
            stripper.setStartPage( 0 );
            stripper.setEndPage( document.getNumberOfPages() );
  
            Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
            stripper.writeText(document, dummy);
            
        }
        finally {
            if( document != null ) {
                document.close();
            }
        }
    }
  
    /**
     * Override the default functionality of PDFTextStripper.writeString()
     */
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
    	TextPosition first = textPositions.get(0);
    	TextPosition last = textPositions.get(textPositions.size()-1);
    	float x = first.getXDirAdj();
    	float y = first.getYDirAdj();
    	float h = textPositions.get(0).getHeight();
    	float w = last.getWidthDirAdj()+last.getXDirAdj() - first.getXDirAdj();
//        for (TextPosition text : textPositions) {
//            System.out.print(text.getUnicode()+ " [(X=" + text.getXDirAdj() + ",Y=" +
//                    text.getYDirAdj() + ") height=" + text.getHeightDir() + " width=" +
//                    text.getWidthDirAdj() + "]");
//            System.out.println("\t [EndX="+text.getEndX()+"]");
//        }
        
        System.out.println(String.format("String: %s [%f, %f, %f, %f ] - [%f, %f]",string, x,y,w,h, first.getEndX(), first.getEndY()));
    }
}
