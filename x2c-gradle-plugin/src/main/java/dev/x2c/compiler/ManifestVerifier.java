package dev.x2c.compiler;

import java.io.File;
import java.util.Collection;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Verifies that a resource-free plugin JAR is not silently dropping manifest semantics. */
public final class ManifestVerifier {
    public void verify(Collection<File> manifests) {
        verify(manifests, true);
    }

    /** Normal AAR integration preserves and merges its manifest, so only plugin mode is strict. */
    public void verify(Collection<File> manifests, boolean pluginMode) {
        if (!pluginMode) {
            return;
        }
        for (File manifest : manifests) {
            if (!manifest.isFile()) {
                continue;
            }
            verifyOne(manifest);
        }
    }

    private static void verifyOne(File manifest) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            disableExternalAccessProperties(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException error) throws SAXException {
                    throw error;
                }

                @Override
                public void fatalError(SAXParseException error) throws SAXException {
                    throw error;
                }
            });
            Element root = builder.parse(manifest).getDocumentElement();
            if (!root.getTagName().equals("manifest")) {
                throw failure(manifest, "root must be <manifest>");
            }
            NamedNodeMap attributes = root.getAttributes();
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                if (!attribute.getNodeName().startsWith("xmlns") && !attribute.getNodeName().equals("package")) {
                    throw failure(manifest, "JAR mode cannot preserve manifest attribute: " + attribute.getNodeName());
                }
            }
            NodeList children = root.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child instanceof Element) {
                    Element element = (Element) child;
                    throw failure(manifest,
                            "JAR mode cannot preserve <" + element.getTagName()
                                    + ">. Move this contract to the consumer manifest or keep an AAR.");
                }
            }
        } catch (ResourceCompilationException error) {
            throw error;
        } catch (Exception error) {
            throw new ResourceCompilationException(manifest.getAbsolutePath() + ": cannot verify AndroidManifest.xml", error);
        }
    }

    private static void disableExternalAccessProperties(DocumentBuilderFactory factory) {
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // AGP 3.5-8.0 can place an older Xerces implementation on the plugin classpath. The
            // disallow-doctype and external-entity features above remain the security boundary.
        }
    }

    private static ResourceCompilationException failure(File file, String message) {
        return new ResourceCompilationException(file.getAbsolutePath() + ": " + message);
    }
}
