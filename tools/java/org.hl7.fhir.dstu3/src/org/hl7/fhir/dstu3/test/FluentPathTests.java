package org.hl7.fhir.dstu3.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.hl7.fhir.dstu3.formats.XmlParser;
import org.hl7.fhir.dstu3.model.Base;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.ExpressionNode;
import org.hl7.fhir.dstu3.model.PrimitiveType;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.test.TestingUtilities;
import org.hl7.fhir.dstu3.utils.FluentPathEngine;
import org.hl7.fhir.dstu3.utils.SimpleWorkerContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xml.XMLUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import junit.framework.Assert;

@RunWith(Parameterized.class)
public class FluentPathTests {

  private static FluentPathEngine fp;

  @Parameters(name = "{index}: file {0}")
  public static Iterable<Object[]> data() throws ParserConfigurationException, SAXException, IOException {
    Document dom = XMLUtil.parseFileToDom("C:\\work\\fluentpath\\spec\\tests-fhir-r3.xml");
    
    List<Element> list = new ArrayList<Element>();
    List<Element> groups = new ArrayList<Element>();
    XMLUtil.getNamedChildren(dom.getDocumentElement(), "group", groups);
    for (Element g : groups) {
      XMLUtil.getNamedChildren(g, "test", list);      
    }

    List<Object[]> objects = new ArrayList<Object[]>(list.size());

    for (Element e : list) {
      objects.add(new Object[] { getName(e), e });
    }

    return objects;
  }

  private static Object getName(Element e) {
    String s = e.getAttribute("name");
    if (Utilities.noString(s)) {
      Element p = (Element) e.getParentNode();
      int ndx = 0;
      for (int i = 0; i < p.getChildNodes().getLength(); i++) {
        Node c = p.getChildNodes().item(i);
        if (c == e)
          break;
        else if (c instanceof Element)
          ndx++;
      }
      s = p.getAttribute("name")+" - "+Integer.toString(ndx+1);
    }
    return s;
  }

  private final Element test;
  private final String name;

  public FluentPathTests(String name, Element e) {
    this.name = name;
    this.test = e;
  }

  @SuppressWarnings("deprecation")
  @Test
  public void test() throws FileNotFoundException, IOException, FHIRException, org.hl7.fhir.dstu3.exceptions.FHIRException {
    if (TestingUtilities.context == null)
      TestingUtilities.context = SimpleWorkerContext.fromPack("C:\\work\\org.hl7.fhir\\build\\publish\\definitions.xml.zip");
    if (fp == null)
      fp = new FluentPathEngine(TestingUtilities.context);
    String input = test.getAttribute("inputfile");
    String expression = XMLUtil.getNamedChild(test, "expression").getTextContent();
    boolean fail = "true".equals(XMLUtil.getNamedChild(test, "expression").getAttribute("invalid"));
    Resource res = null;
    
    List<Base> outcome = new ArrayList<Base>();

    ExpressionNode node = fp.parse(expression);
    try {
      if (Utilities.noString(input))
        fp.check(null, null, null, node);
      else {
        res = new XmlParser().parse(new FileInputStream(Utilities.path("C:\\work\\org.hl7.fhir\\build\\publish", input)));
        fp.check(res, res.getResourceType().toString(), res.getResourceType().toString(), node);
      }
      outcome = fp.evaluate(res, node);
      Assert.assertTrue(String.format("Expected exception parsing %s", expression), !fail);
    } catch (Exception e) {
      Assert.assertTrue(String.format("Unexpected exception parsing %s: "+e.getMessage(), expression), fail);
    }
      
    if ("true".equals(test.getAttribute("predicate"))) {
      boolean ok = fp.convertToBoolean(outcome);
      outcome.clear();
      outcome.add(new BooleanType(ok));
    }
    if (fp.hasLog())
      System.out.println(fp.takeLog());

    List<Element> expected = new ArrayList<Element>();
    XMLUtil.getNamedChildren(test, "output", expected);
    Assert.assertTrue(String.format("Expected %d objects but found %d", expected.size(), outcome.size()), outcome.size() == expected.size());
    for (int i = 0; i < Math.min(outcome.size(), expected.size()); i++) {
      String tn = expected.get(i).getAttribute("type");
      if (!Utilities.noString(tn)) {
        Assert.assertTrue(String.format("Outcome %d: Type should be %s but was %s", i, tn, outcome.get(i).fhirType()), tn.equals(outcome.get(i).fhirType()));
      }
      String v = expected.get(i).getTextContent();
      if (!Utilities.noString(v)) {
        Assert.assertTrue(String.format("Outcome %d: Value should be a primitive type but was %s", i, outcome.get(i).fhirType()), outcome.get(i) instanceof PrimitiveType);
        Assert.assertTrue(String.format("Outcome %d: Value should be %s but was %s", i, v, outcome.get(i).toString()), v.equals(((PrimitiveType)outcome.get(i)).asStringValue()));
      } 
    }
  }

}
