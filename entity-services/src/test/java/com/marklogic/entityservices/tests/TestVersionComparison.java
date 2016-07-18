package com.marklogic.entityservices.tests;

import com.marklogic.client.document.DocumentManager;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.eval.EvalResult;
import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;

/**
 */
public class TestVersionComparison extends EntityServicesTestBase {


    DocumentManager documentManager;
    String entityTypeTarget = "conversion-target.json";
    String entityTypeSource = "conversion-source.json";

    @Before
    public void generateArtifacts() throws TestEvalException, IOException {

        setupClients();
        InputStream is = this.getClass().getResourceAsStream("/entity-type-units/" + entityTypeTarget);
        documentManager = client.newJSONDocumentManager();
        documentManager.write(entityTypeTarget, new InputStreamHandle(is).withFormat(Format.JSON));
        is = this.getClass().getResourceAsStream("/entity-type-units/" + entityTypeSource);
        documentManager.write(entityTypeSource, new InputStreamHandle(is).withFormat(Format.JSON));

    }

    //@After
    public void remove() {

        documentManager = client.newJSONDocumentManager();
        documentManager.delete(entityTypeTarget);
        documentManager.delete(entityTypeSource);
        modulesClient.newTextDocumentManager().delete("/ext/version-comparison.xqy");

    }


    @Test
    public void testVersionComparison() throws TestEvalException, IOException, SAXException, TransformerException {
        EvalResultIterator results =
            eval("let $source := doc('"+entityTypeSource+"')=>es:entity-type-from-node() "+
                          "let $target := doc('"+entityTypeTarget+"')=>es:entity-type-from-node() "+
                          "return (es:conversion-module-generate($target), "+
                          "es:version-comparison-generate($source, $target))");

        TextDocumentManager mgr = modulesClient.newTextDocumentManager();

        StringHandle handle = results.next().get(new StringHandle());
        mgr.write("/ext/comparison-0.0.2.xqy", handle);
        handle = results.next().get(new StringHandle());
        mgr.write("/ext/version-comparison.xqy", handle);
        results.close();

        String instance1 = "instance-0.0.1.xml";
        InputStream is = this.getClass().getResourceAsStream("/entity-type-units/" + instance1);
        documentManager.write(instance1, new InputStreamHandle(is).withFormat(Format.XML));

        DOMHandle domHandle = evalOneResult("import module namespace c = 'http://example.org/tests/conversion-0.0.2-from-conversion-0.0.1' at '/ext/version-comparison.xqy';" +
                               "import module namespace m = 'http://example.org/tests/conversion-0.0.2' at '/ext/comparison-0.0.2.xqy';" +
                "<x>{" +
                "doc('instance-0.0.1.xml')/x=>c:convert-instance-ETOne()=>m:instance-to-canonical-xml()," +
                "doc('instance-0.0.1.xml')/x=>c:convert-instance-ETTwo()=>m:instance-to-canonical-xml()," +
                "doc('instance-0.0.1.xml')/x=>c:convert-instance-ETThree()=>m:instance-to-canonical-xml()" +
                "}</x>", new DOMHandle());

        String expected = "instance-0.0.2.xml";
        is = this.getClass().getResourceAsStream("/entity-type-units/" + expected);
        Document expectedDoc = builder.parse(is);
        Document actualDoc = domHandle.get();

        //save("expected.xml", expectedDoc);
        //save("actual.xml", actualDoc);

        XMLUnit.setIgnoreWhitespace(true);
        XMLAssert.assertXMLEqual("checking instance conversion to target", actualDoc, expectedDoc);
        logger.info(handle.get());


    }
}