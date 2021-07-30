import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import com.copart.emails.vo.PDFparseDetails;
import com.copart.emails.vo.PDFparseDetails.CoOrdinates;
import com.copart.emails.vo.PDFparseDetails.PDFPageDetails;
import com.copart.emails.vo.PDFparseDetails.RegionDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PdfToJsonUtil {
	
	public static final String KEY_VLAUE_SEPARATOR = ":";
	public static final String TAB = "\t";
	
	public static final class RegionType{
		private RegionType() {
			
		}
		public static final Character COMPLEX_REGION = 'C'; //At-least one value with key in multiple lines in a single region (or) At-most one value without key
		public static final Character SIMPLE_REGION = 'S'; //Value with key value pair separated by colon (:) in single line
		public static final Character COMPLEX_REGION_WITH_MULTIPLE_PLACES = 'M'; // This is a region where the value should be fetch from different regions and also the value concatenated with keys 
	}
	
	
	@Autowired
	private ObjectMapper mapper;
	
	public String getJsonFromPDF(InputStream inputStream, PDFparseDetails pdfparseDetails) {
		return getJsonFromPDF(inputStream, pdfparseDetails, null);
	}
	
	public String getJsonFromPDF(InputStream inputStream, PDFparseDetails pdfparseDetails, Map<String, String> additionalDetails) {
		try(PDDocument doc = PDDocument.load(inputStream)){
			if (!doc.isEncrypted()) {
				ObjectNode rootNode = mapper.createObjectNode(); 
				List<PDFPageDetails> pdfPageDetailsList = pdfparseDetails.getPdfPageDetailsList();
				pdfPageDetailsList.stream()
				.forEach(pdfPageDetails -> {
					List<RegionDetails> regionDetailsList = pdfPageDetails.getRegionDetailsList();
					PDPage page = doc.getPage(pdfPageDetails.getPageNumber());
					regionDetailsList.stream().forEach(regionDetails -> readRegionsAndSetJsonNode(page, regionDetails, rootNode));
				});
				
				String jon = mapper.writeValueAsString(rootNode);

				log.info("PDF to Json: {}", json);
				
				return json;
			}else {
				log.error("Attachment is encrypted, Unable to parse!!!");
				return null;
			}
		} catch (IOException e) {
			log.error("Error while loading the PDF", e);
			return null;
		}
	}
	
	public void readRegionsAndSetJsonNode(PDPage page, RegionDetails regionDetails, ObjectNode rootNode) {
		String regionName = regionDetails.getName();
		CoOrdinates co_ordinates = regionDetails.getCoOrdinates();
		Map<String, String> mappings = regionDetails.getMappings();
		Character type = regionDetails.getType();
		try {
			PDFTextStripperByArea stripper = new PDFTextStripperByArea();
			Rectangle2D region = new Rectangle2D.Double(
					co_ordinates.getX(),
					co_ordinates.getY(),
					co_ordinates.getW(),
					co_ordinates.getH());
			stripper.addRegion(regionName, region);
			stripper.extractRegions(page);
			String text = StringUtils.trim(stripper.getTextForRegion(regionName));
			if(StringUtils.isBlank(text)) {
				log.info("Nothing found at region({}): {}", regionName, co_ordinates);
				return;
			}

			Map<String, String> keyValuesMap;
			if(type.equals(RegionType.SIMPLE_REGION)) { 
				keyValuesMap = extractSimpleValues(regionName, text);
			}else {
				keyValuesMap = extractComplexValues(regionName, text, type);
			}
			
			if(! keyValuesMap.isEmpty()) {
				keyValuesMap.forEach((key, value) -> {
					if(mappings.containsKey(key)) {
						String assignmentServiceFieldName = mappings.get(key);
						if(type.equals(RegionType.COMPLEX_REGION_WITH_MULTIPLE_PLACES)) {
							setJsonHeirarchy(rootNode, assignmentServiceFieldName, value, true);
						}else {
							setJsonHeirarchy(rootNode, assignmentServiceFieldName, value); 	
						}
					}
				});
			}
		} catch (IOException e) {
			log.error("Exception occured while parsing the pdf ", e);
		}
	}

	public Map<String, String> extractSimpleValues(String regionName, String text) {
		String[] lines = text.split(StringUtils.LF);
		return Arrays.stream(lines)
				.filter(StringUtils::isNotBlank)
				.map(line -> line.split(TAB))
				.flatMap(Arrays::stream)
				.map(pair -> this.buildPairOrLogIfError(regionName, pair))
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
	
	public Pair<String, String> buildPairOrLogIfError(String regionName, String pair){
		String[] keyValue =pair.replaceAll("[\\n\\r]+", "").split(KEY_VLAUE_SEPARATOR);
		if(keyValue.length>2) {
			log.warn("Ignoring, because we found more than one colon(:) in this line - {} @ region: {}", pair, regionName);
			return null;
		}else if (keyValue.length == 0){
			log.warn("Ignoring, because no colon found in this line = {} @ region: {}", pair, regionName);
			return null;
		}else if(keyValue.length == 1){
			return null;//just ignore as value may blank
		}else {//keyValue.length == 2
			return Pair.of(keyValue[0].trim(), keyValue[1].trim());
		}
	}
	
	
	public Map<String, String> extractComplexValues(String regionName, String text, Character type) {
		Map<String, String> keyValuesMap = new HashMap<>();
		if(! text.contains(KEY_VLAUE_SEPARATOR)) {
			keyValuesMap.put(regionName+"_0", text);
		}else {
			String[] lines = text.replaceAll(StringUtils.CR, StringUtils.EMPTY).split(StringUtils.LF);
			String key = StringUtils.EMPTY;
			String value = StringUtils.EMPTY;
			for(String line: lines) {
				if(line.contains(KEY_VLAUE_SEPARATOR)) {
					if(StringUtils.isNotBlank(value)) {
						if(StringUtils.isNotBlank(key)) {
							if(RegionType.COMPLEX_REGION_WITH_MULTIPLE_PLACES.equals(type)) {
								value = key+KEY_VLAUE_SEPARATOR+value;
							}
							keyValuesMap.put(key, value.trim());
							key = StringUtils.EMPTY;
							value = StringUtils.EMPTY;
						}else {
							keyValuesMap.put(regionName+"_0", value.trim());
							value = StringUtils.EMPTY;
						}
					}
					String[] keyValue = line.split(KEY_VLAUE_SEPARATOR);
					key = keyValue[0].trim();
					if(keyValue.length==2) {
						value = keyValue[1].trim();
					}
				}else {
					value = value+StringUtils.CR+line;
				}
			}
			
			if(StringUtils.isNotBlank(value)) {
				if(StringUtils.isNotBlank(key)) {
					if(RegionType.COMPLEX_REGION_WITH_MULTIPLE_PLACES.equals(type)) {
						value = key+KEY_VLAUE_SEPARATOR+value;
					}
					keyValuesMap.put(key, value);
				}else {
					keyValuesMap.put(regionName+"_0", value);
				}
			}
		}
		
		return keyValuesMap;
	}
	
	public void setJsonHeirarchy(ObjectNode node, String fieldName, String value) {
		setJsonHeirarchy(node, fieldName, value, false);
	}
	
	private void setJsonHeirarchy(ObjectNode node, String fieldName, String value, boolean appendIfExist) {
		if(fieldName.contains(".")) {
			int indexOfDot = fieldName.indexOf(".");
			String childNodeName = fieldName.substring(0, indexOfDot);
			String remainingHeirarchyString = fieldName.substring(indexOfDot+1);
			JsonNode childNode = node.path(childNodeName); 
			if(childNode.isMissingNode()) {
				childNode = mapper.createObjectNode();
				node.set(childNodeName, childNode); 
			}			
			setJsonHeirarchy((ObjectNode)childNode, remainingHeirarchyString, value, appendIfExist);
		}else {
			if(appendIfExist && !node.path(fieldName).isMissingNode()) {
				value = node.get(fieldName).textValue()+StringUtils.LF+value;
			}
			node.put(fieldName, value);
		}	
	}
}

