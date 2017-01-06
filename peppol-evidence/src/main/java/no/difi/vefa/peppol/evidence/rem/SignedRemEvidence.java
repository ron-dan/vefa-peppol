/*
 * Copyright 2016-2017 Direktoratet for forvaltning og IKT
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package no.difi.vefa.peppol.evidence.rem;

import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.InstanceIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.Scheme;
import no.difi.vefa.peppol.evidence.jaxb.receipt.PeppolRemExtension;
import no.difi.vefa.peppol.evidence.jaxb.rem.*;
import no.difi.vefa.peppol.evidence.jaxb.xades.AnyType;
import no.difi.vefa.peppol.evidence.lang.RemEvidenceException;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBElement;
import java.util.Date;
import java.util.List;

/**
 * Holds a signed REMEvidence. Internally it is held in two representations; REMEvidenceType and
 * W3C Document.
 * <p/>
 * Please use {@link RemEvidenceTransformer} to transform instances of SignedRemEvidence into other
 * representations like for instance XML and JAXB
 *
 * @author steinar
 *         Date: 27.11.2015
 *         Time: 11.50
 */
public class SignedRemEvidence {

    private final JAXBElement<REMEvidenceType> jaxbElement;
    private final Document signedRemEvidenceXml;

    public SignedRemEvidence(JAXBElement<REMEvidenceType> jaxbElement, Document signedRemEvidenceXml) {
        this.jaxbElement = jaxbElement;
        this.signedRemEvidenceXml = signedRemEvidenceXml;
    }

    /**
     * Provides access to the REM evidence in accordance with the XML schema. Thus allowing simple access to various
     * fields without reverting to XPath expressions in the W3C Document.
     */
    public REMEvidenceType getRemEvidenceType() {
        return e();
    }

    public Document getDocument() {
        return signedRemEvidenceXml;
    }

    public EvidenceTypeInstance getEvidenceType() {
        try {
            String evElementName = signedRemEvidenceXml.getDocumentElement().getLocalName();
            switch (evElementName) {
                case "DeliveryNonDeliveryToRecipient":
                    return EvidenceTypeInstance.DELIVERY_NON_DELIVERY_TO_RECIPIENT;
                case "RelayREMMDAcceptanceRejection":
                    return EvidenceTypeInstance.RELAY_REM_MD_ACCEPTANCE_REJECTION;
                default:
                    return null;
            }
        } catch (NullPointerException npe) {
            return null;
        }
    }

    public String getEvidenceIdentifier() {
        return e().getEvidenceIdentifier();
    }

    public EventCode getEventCode() {
        return EventCode.valueFor(e().getEventCode());
    }

    public EventReason getEventReason() {
        assert e() != null : "jaxbElement.getValue() returned null";
        assert e().getEventReasons() != null : "There are no event reasons";
        assert e().getEventReasons().getEventReason() != null : "getEventReasons() returned null";
        assert !e().getEventReasons().getEventReason().isEmpty() : "List of event reasons is empty";

        EventReasonType eventReasonType = e().getEventReasons().getEventReason().get(0);
        return EventReason.valueForCode(eventReasonType.getCode());
    }

    public Date getEventTime() {
        return e().getEventTime().toGregorianCalendar().getTime();
    }

    public String getEvidenceIssuerPolicyID() throws RemEvidenceException {
        if (e().getEvidenceIssuerPolicyID() == null)
            throw new RemEvidenceException("Evidence issuer policy ID is not set");
        else
            return e().getEvidenceIssuerPolicyID().getPolicyID().get(0);
    }

    public String getEvidenceIssuerDetails() throws RemEvidenceException {
        try {
            return e().getEvidenceIssuerDetails()
                    .getNamesPostalAddresses().getNamePostalAddress().get(0).getEntityName().getName().get(0);
        } catch (NullPointerException npe) {
            throw new RemEvidenceException("There are no Event Issuer Details");
        }
    }

    public ParticipantIdentifier getSenderIdentifier() {

        EntityDetailsType senderDetails = e().getSenderDetails();
        List<Object> attributedElectronicAddressOrElectronicAddress = senderDetails.getAttributedElectronicAddressOrElectronicAddress();

        AttributedElectronicAddressType attributedElectronicAddressType = (AttributedElectronicAddressType) attributedElectronicAddressOrElectronicAddress.get(0);
        String scheme = attributedElectronicAddressType.getScheme();
        String value = attributedElectronicAddressType.getValue();

        return ParticipantIdentifier.of(value, Scheme.of(scheme));
    }


    /**
     * Internal convenience method
     */
    private REMEvidenceType e() {
        return jaxbElement.getValue();
    }

    public ParticipantIdentifier getRecipientIdentifier() {
        EntityDetailsListType entityDetailsListType = e().getRecipientsDetails();
        EntityDetailsType entityDetailsType = entityDetailsListType.getEntityDetails().get(0);
        List<Object> objectList = entityDetailsType.getAttributedElectronicAddressOrElectronicAddress();

        AttributedElectronicAddressType attributedElectronicAddressType = (AttributedElectronicAddressType) objectList.get(0);
        String scheme = attributedElectronicAddressType.getScheme();
        String value = attributedElectronicAddressType.getValue();


        return ParticipantIdentifier.of(value, Scheme.of(scheme));
    }

    public DocumentTypeIdentifier getDocumentTypeIdentifier() {
        MessageDetailsType senderMessageDetails = e().getSenderMessageDetails();
        String messageSubject = senderMessageDetails.getMessageSubject();

        return DocumentTypeIdentifier.of(messageSubject);
    }

    public String getDocumentTypeInstanceIdentifier() {
        return e().getSenderMessageDetails().getUAMessageIdentifier();
    }

    public InstanceIdentifier getInstanceIdentifier() {
        String remMDMessageIdentifier = e().getSenderMessageDetails().getMessageIdentifierByREMMD();

        return InstanceIdentifier.of(remMDMessageIdentifier);
    }

    public byte[] getPayloadDigestValue() {
        assert e() != null : "jaxbElement.getValue() returned null";
        assert e().getSenderMessageDetails() != null : "getSenderMessageDetails() returned null";

        return e().getSenderMessageDetails().getDigestValue();
    }

    @SuppressWarnings("unchecked")
    public PeppolRemExtension getTransmissionEvidence() {

        ExtensionType extensionType = e().getExtensions().getExtension().get(0);

        JAXBElement<AnyType> anyType = (JAXBElement<AnyType>) extensionType.getContent().get(0);
        AnyType value = anyType.getValue();

        return (PeppolRemExtension) value.getContent().get(0);
    }
}
