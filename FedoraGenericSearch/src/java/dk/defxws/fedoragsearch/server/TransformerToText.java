//$Id$
/*
 * <p><b>License and Copyright: </b>The contents of this file is subject to the
 * same open source license as the Fedora Repository System at www.fedora-commons.org
 * Copyright &copy; 2006, 2007, 2008, 2009, 2010, 2011 by The Technical University of Denmark.
 * All rights reserved.</p>
 */
package dk.defxws.fedoragsearch.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.StringTokenizer;

import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;
import org.apache.lucene.benchmark.byTask.feeds.demohtml.HTMLParser;
//import org.apache.lucene.demo.html.HTMLParser;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import dk.defxws.fedoragsearch.server.errors.GenericSearchException;

/**
 * performs transformations from formatted documents to text
 * 
 * @author  gsp@dtv.dk
 * @version 
 */
public class TransformerToText {
    
    private static final Logger logger =
        Logger.getLogger(TransformerToText.class);
    
    public static final String[] handledMimeTypes = {"text/plain", "text/xml", "application/xml",  "text/html", "application/pdf"};
    
    public TransformerToText() {
    }
    
    public StringBuffer getFromTika(byte[] doc, String pluginName, String indexfieldnamePrefix, String selectedFieldnames) 
    throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        InputStream isr = new ByteArrayInputStream(doc);
        BodyContentHandler textHandler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        try {
			parser.parse(isr, textHandler, metadata);
		} catch (IOException e) {
            throw new GenericSearchException(e.toString());
		} catch (SAXException e) {
            throw new GenericSearchException(e.toString());
		} catch (TikaException e) {
            throw new GenericSearchException(e.toString());
		} finally {
			try {
				isr.close();
			} catch (IOException e) {
	            throw new GenericSearchException(e.toString());
			}
		}
		docText.append(textHandler.toString().replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("&", "&amp;").replaceAll("\"", "&quot;"));
		if ("Lucene".equals(pluginName) || "Solr".equals(pluginName)) {
	        String[] names = metadata.names();
	        if (selectedFieldnames != null && selectedFieldnames.length()>0){
	        	names = selectedFieldnames.split(",");
	        }
	        for (int i=0; i<names.length; i++) {
	        	String metadataName = names[i].trim();
	        	if (metadataName.length() > 0) {
		        	String[] metadataNameWithParams = metadataName.split("/");
		        	metadataName = metadataNameWithParams[0].trim();
		        	String index = "TOKENIZED";
		        	String store = "YES";
		        	String termVector = "YES";
		        	String boost = "1.0";
		        	if (metadataNameWithParams.length > 1) {
		        		if (metadataNameWithParams[1].length() > 0)
		        			index = metadataNameWithParams[1];
			        	if (metadataNameWithParams.length > 2) {
			        		if (metadataNameWithParams[2].length() > 0)
			        			store = metadataNameWithParams[2];
				        	if (metadataNameWithParams.length > 3) {
				        		if (metadataNameWithParams[3].length() > 0)
				        			termVector = metadataNameWithParams[3];
					        	if (metadataNameWithParams.length > 4) {
					        		if (metadataNameWithParams[4].length() > 0)
					        			boost = metadataNameWithParams[4];
					        	}
				        	}
			        	}
		        	}
		        	StringBuffer metadataValue = new StringBuffer(metadata.get(metadataName));
		        	String[] metadataValues = metadata.getValues(metadataName);
		        	if (metadataValues.length>1) {
		        		for (int j=1; j<metadataValues.length; j++) {
		        			metadataValue.append(" "+metadataValues[j]);
		        		}
		        	}
		        	if (metadataValue.length()>0) {
			        	if ("Lucene".equals(pluginName)) {
				        	docText.append("</IndexField>\n<IndexField IFname=\""+indexfieldnamePrefix+metadataName+"\" index=\""+index+"\" store=\""+store+"\" termVector=\""+termVector+"\" boost=\""+boost+"\">");
			        	} else if ("Solr".equals(pluginName)) {
				        	docText.append("</field>\n<field name=\""+indexfieldnamePrefix+metadataName+"\">");
			        	}
			        	docText.append(metadataValue.toString().replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("&", "&amp;").replaceAll("\"", "&quot;"));
		        	}
	        	}
	        }
		}
        return docText;
    }
    
    public StringBuffer getText(byte[] doc, String mimetype) 
    throws GenericSearchException {
        if (mimetype.equals("text/plain")) {
            return getTextFromText(doc);
        } else if(mimetype.equals("text/xml") || mimetype.equals("application/xml")) {
            return getTextFromXML(doc);
        } else if(mimetype.equals("text/html")) {
            return getTextFromHTML(doc);
        } else if(mimetype.equals("application/pdf")) {
            return getTextFromPDF(doc);
        } else if(mimetype.equals("application/ps")) {
            return new StringBuffer();
        } else if(mimetype.equals("application/msword")) {
            return new StringBuffer();
        } else return new StringBuffer();
    }
    
    private StringBuffer getTextFromText(byte[] doc) 
    throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(doc));
        try {
            int c = isr.read();
            while (c>-1) {
                docText.append((char)c);
                c=isr.read();
            }
        } catch (IOException e) {
            throw new GenericSearchException(e.toString());
        }
        return docText;
    }

    private StringBuffer getTextFromXML(byte[] doc) 
    throws GenericSearchException {
    	InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(doc));
    	StringBuffer docText = (new GTransformer()).transform(
    		"fgsconfigFinal/textFromXml", 
            new StreamSource(isr));
    	docText.delete(0, docText.indexOf(">")+1);
    	return docText;
    }

    private StringBuffer getTextFromHTML(byte[] doc) 
    throws GenericSearchException {
        StringBuffer docText = new StringBuffer();
        HTMLParser htmlParser = new HTMLParser(new ByteArrayInputStream(doc));
        try {
            InputStreamReader isr = (InputStreamReader) htmlParser.getReader();
            int c = isr.read();
            while (c>-1) {
                docText.append((char)c);
                c=isr.read();
            }
        } catch (IOException e) {
            throw new GenericSearchException(e.toString());
        }
        return docText;
    }

    private StringBuffer getTextFromPDF(byte[] doc) 
    throws GenericSearchException {
//      extract PDF document's textual content
    	
//      encrypted and/or password protected PDF documents cannot be indexed by GSearch,
//      an exception will be thrown here, which will be caught by the calling
//      GenericOperationsImpl method, which will return an empty index field

        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF");
        StringBuffer docText = new StringBuffer();
        ByteArrayInputStream bais = null;
		try {
			bais = new ByteArrayInputStream(doc);
		} catch (Exception e) {
			closeBAIS(bais);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF new ByteArrayInputStream: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF new ByteArrayInputStream: ", e);
		}
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF new ByteArrayInputStream");
        PDFParser parser;
		try {
			parser = new PDFParser(bais);
		} catch (Exception e) {
			closeBAIS(bais);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF new PDFParser: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF new PDFParser: ", e);
		}
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF new PDFParser");
        try {
			parser.parse();
		} catch (Exception e) {
			closeBAIS(bais);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF parser.parse: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF parser.parse: ", e);
		}
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF parser.parse");
        COSDocument cosDoc = null;
        try {
            cosDoc = parser.getDocument();
        }
        catch (Exception e) {
			closeBAIS(bais);
            closeCOSDocument(cosDoc);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF parser.getDocument: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF parser.getDocument: ", e);
        }
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF parser.getDocument");
        PDDocument pdDoc = null;
        try {
			pdDoc = new PDDocument(cosDoc);
		} catch (Exception e) {
			closeBAIS(bais);
            closeCOSDocument(cosDoc);
            closePDDocument(pdDoc);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF new PDDocument: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF new PDDocument: ", e);
        }
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF new PDDocument isEncrypted="+pdDoc.isEncrypted()+" getNumberOfPages="+pdDoc.getNumberOfPages());
        PDFTextStripper stripper;
		try {
			stripper = new PDFTextStripper();
		} catch (Exception e) {
			closeBAIS(bais);
            closeCOSDocument(cosDoc);
            closePDDocument(pdDoc);
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF new PDFTextStripper: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF new PDFTextStripper: ", e);
        }
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF new PDFTextStripper getStartPage="+stripper. getStartPage()+" getEndPage="+stripper.getEndPage());
        String docString = "";
        try {
            docString = stripper.getText(pdDoc);
        }
        catch (Exception e) {
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF stripper.getText: ", e);
            throw new GenericSearchException(
                    "getTextFromPDF stripper.getText: ", e);
        }
        finally {
            if (logger.isDebugEnabled())
                logger.debug("getTextFromPDF stripper.getText finally");
			closeBAIS(bais);
            closeCOSDocument(cosDoc);
            closePDDocument(pdDoc);
        }
        if (logger.isDebugEnabled())
            logger.debug("getTextFromPDF stripper.getText");
        docText = new StringBuffer(docString);
//      put space instead of characters not allowed in the indexing stylesheet
        char c;
      	for (int i=0; i<docText.length(); i++) {
      		c = docText.charAt(i);
        	if (c < 32 && c != 9 && c != 10 && c != 13) {
                if (logger.isDebugEnabled())
                	logger.debug("getTextFromPDF index="+i+" char="+c+" set to 32");
                docText.replace(i, i+1, " ");
        	}
        }
        return docText;
    }
    
    private void closeCOSDocument(COSDocument cosDoc) 
    throws GenericSearchException {
        if (cosDoc != null) {
            try {
                cosDoc.close();
            }
            catch (Exception e) {
                throw new GenericSearchException(
                        "Cannot close COSDocument: ", e);
            }
        }
    }
    
    private void closePDDocument(PDDocument pdDoc) 
    throws GenericSearchException {
        if (pdDoc != null) {
            try {
            	pdDoc.close();
            }
            catch (Exception e) {
                throw new GenericSearchException(
                        "Cannot close PDDocument: ", e);
            }
        }
    }
    
    private void closeBAIS(ByteArrayInputStream bais) 
    throws GenericSearchException {
        if (bais != null) {
            try {
            	bais.close();
            }
            catch (Exception e) {
                throw new GenericSearchException(
                        "Cannot close ByteArrayInputStream: ", e);
            }
        }
    }
}
