package net.robinfriedli.botify.entities.xml;

import java.util.List;

import javax.annotation.Nullable;

import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class Version extends AbstractXmlElement implements Comparable<Version> {

    public Version(Element element, Context context) {
        super(element, context);
    }

    public Version(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getVersion();
    }

    public String getVersion() {
        return getAttribute("version").getValue();
    }

    public boolean isLaunched() {
        return getAttribute("launched").getBool();
    }

    public boolean isSilentUpdate() {
        return getAttribute("silent").getBool();
    }

    public List<Feature> getFeatures() {
        return getSubElementsWithType(Feature.class);
    }

    @Override
    public int compareTo(@NotNull Version o) {
        String version1 = getVersion();
        String version2 = o.getVersion();

        return VersionManager.compareVersionString(version1, version2);
    }

    public static class Feature extends AbstractXmlElement {

        // invoked by JXP
        @SuppressWarnings("unused")
        public Feature(Element element, Context context) {
            super(element, context);
        }

        // invoked by JXP
        @SuppressWarnings("unused")
        public Feature(Element element, List<XmlElement> subElements, Context context) {
            super(element, subElements, context);
        }

        @Nullable
        @Override
        public String getId() {
            return null;
        }

        public String getFeatureText() {
            return getTextContent();
        }

    }

}
