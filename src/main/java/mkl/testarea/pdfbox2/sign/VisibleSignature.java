package mkl.testarea.pdfbox2.sign;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Calendar;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.examples.signature.CreateSignatureBase;
import org.apache.pdfbox.examples.signature.SigUtils;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigProperties;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Hex;

/**
 * <a href="https://stackoverflow.com/questions/50224181/pdfbox-2-0-8-issue-while-signing-document">
 * PDFBox 2.0.8 issue while signing document
 * </a>
 * <p>
 * The cause was that the OP chose a PDDocument.addSignature overload for
 * external signing while implementing otherwise internal signing. Choosing
 * an appropriate overload fixed the problem.
 * </p>
 * <p>
 * This is a copy of the PDFBox example class `org.apache.pdfbox.examples.signature.CreateVisibleSignature`
 * changed as indicated by the OP of the question.
 * </p>
 */
public class VisibleSignature extends CreateSignatureBase
{
    private SignatureOptions signatureOptions;
    private PDVisibleSignDesigner visibleSignDesigner;
    private final PDVisibleSigProperties visibleSignatureProperties = new PDVisibleSigProperties();
    private boolean lateExternalSigning = false;

    public boolean isLateExternalSigning()
    {
        return lateExternalSigning;
    }

    /**
     * Set late external signing. Enable this if you want to activate the demo code where the
     * signature is kept and added in an extra step without using PDFBox methods. This is disabled
     * by default.
     *
     * @param lateExternalSigning
     */
    public void setLateExternalSigning(boolean lateExternalSigning)
    {
        this.lateExternalSigning = lateExternalSigning;
    }

    /**
     * Set visible signature designer for a new signature field.
     * 
     * @param filename
     * @param x position of the signature field
     * @param y position of the signature field
     * @param zoomPercent increase (positive value) or decrease (negative value) image with x percent.
     * @param imageStream input stream of an image.
     * @param page the signature should be placed on
     * @throws IOException
     */
    public void setVisibleSignDesigner(String filename, int x, int y, int zoomPercent, 
            InputStream imageStream, int page) 
            throws IOException
    {
        visibleSignDesigner = new PDVisibleSignDesigner(filename, imageStream, page);
        visibleSignDesigner.xAxis(x).yAxis(y).zoom(zoomPercent).adjustForRotation();
    }
    
    /**
     * Set visible signature designer for an existing signature field.
     * 
     * @param zoomPercent increase (positive value) or decrease (negative value) image with x percent.
     * @param imageStream input stream of an image.
     * @throws IOException
     */
    public void setVisibleSignDesigner(int zoomPercent, InputStream imageStream) 
            throws IOException
    {
        visibleSignDesigner = new PDVisibleSignDesigner(imageStream);
        visibleSignDesigner.zoom(zoomPercent);
    }
    
    /**
     * Set visible signature properties for new signature fields.
     * 
     * @param name
     * @param location
     * @param reason
     * @param preferredSize
     * @param page
     * @param visualSignEnabled
     */
    public void setVisibleSignatureProperties(String name, String location, String reason, int preferredSize, 
            int page, boolean visualSignEnabled)
    {
        visibleSignatureProperties.signerName(name).signerLocation(location).signatureReason(reason).
                preferredSize(preferredSize).page(page).visualSignEnabled(visualSignEnabled).
                setPdVisibleSignature(visibleSignDesigner);
    }
    
    /**
     * Set visible signature properties for existing signature fields.
     * 
     * @param name
     * @param location
     * @param reason
     * @param visualSignEnabled
     */
    public void setVisibleSignatureProperties(String name, String location, String reason,
            boolean visualSignEnabled)
    {
        visibleSignatureProperties.signerName(name).signerLocation(location).signatureReason(reason).
                visualSignEnabled(visualSignEnabled).setPdVisibleSignature(visibleSignDesigner);
    }

    /**
     * Initialize the signature creator with a keystore (pkcs12) and pin that
     * should be used for the signature.
     *
     * @param keystore is a pkcs12 keystore.
     * @param pin is the pin for the keystore / private key
     * @throws KeyStoreException if the keystore has not been initialized (loaded)
     * @throws NoSuchAlgorithmException if the algorithm for recovering the key cannot be found
     * @throws UnrecoverableKeyException if the given password is wrong
     * @throws CertificateException if the certificate is not valid as signing time
     * @throws IOException if no certificate could be found
     */
    public VisibleSignature(KeyStore keystore, char[] pin)
            throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, CertificateException
    {
        super(keystore, pin);
    }

    /**
     * Sign pdf file and create new file that ends with "_signed.pdf".
     *
     * @param inputFile The source pdf document file.
     * @param signedFile The file to be signed.
     * @param tsaUrl optional TSA url
     * @throws IOException
     */
    public void signPDF(File inputFile, File signedFile, String tsaUrl) throws IOException
    {
        this.signPDF(inputFile, signedFile, tsaUrl, null);
    }

    /**
     * Sign pdf file and create new file that ends with "_signed.pdf".
     *
     * @param inputFile The source pdf document file.
     * @param signedFile The file to be signed.
     * @param tsaUrl optional TSA url
     * @param signatureFieldName optional name of an existing (unsigned) signature field
     * @throws IOException
     */
    public void signPDF(File inputFile, File signedFile, String tsaUrl, String signatureFieldName) throws IOException
    {
        if (inputFile == null || !inputFile.exists())
        {
            throw new IOException("Document for signing does not exist");
        }

        setTsaUrl(tsaUrl);

        // creating output document and prepare the IO streams.
        
        try (
            FileOutputStream fos = new FileOutputStream(signedFile);
            PDDocument doc = PDDocument.load(inputFile)
        ) {
            int accessPermissions = SigUtils.getMDPPermission(doc);
            if (accessPermissions == 1)
            {
                throw new IllegalStateException("No changes to the document are permitted due to DocMDP transform parameters dictionary");
            }
            // Note that PDFBox has a bug that visual signing on certified files with permission 2
            // doesn't work properly, see PDFBOX-3699. As long as this issue is open, you may want to
            // be careful with such files.

            PDSignature signature;

            // sign a PDF with an existing empty signature, as created by the CreateEmptySignatureForm example.
            signature = findExistingSignature(doc, signatureFieldName);

            if (signature == null)
            {
                // create signature dictionary
                signature = new PDSignature();
            }

            // Optional: certify
            // can be done only if version is at least 1.5 and if not already set
            // doing this on a PDF/A-1b file fails validation by Adobe preflight (PDFBOX-3821)
            // PDF/A-1b requires PDF version 1.4 max, so don't increase the version on such files.
            if (doc.getVersion() >= 1.5f && accessPermissions == 0)
            {
                SigUtils.setMDPPermission(doc, signature, 2);
            }

            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm != null && acroForm.getNeedAppearances())
            {
                // PDFBOX-3738 NeedAppearances true results in visible signature becoming invisible 
                // with Adobe Reader
                if (acroForm.getFields().isEmpty())
                {
                    // we can safely delete it if there are no fields
                    acroForm.getCOSObject().removeItem(COSName.NEED_APPEARANCES);
                    // note that if you've set MDP permissions, the removal of this item
                    // may result in Adobe Reader claiming that the document has been changed.
                    // and/or that field content won't be displayed properly.
                    // ==> decide what you prefer and adjust your code accordingly.
                }
                else
                {
                    System.out.println("/NeedAppearances is set, signature may be ignored by Adobe Reader");
                }
            }

            // default filter
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);

            // subfilter for basic and PAdES Part 2 signatures
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);

            if (visibleSignatureProperties != null)
            {
                // this builds the signature structures in a separate document
                visibleSignatureProperties.buildSignature();

                signature.setName(visibleSignatureProperties.getSignerName());
                signature.setLocation(visibleSignatureProperties.getSignerLocation());
                signature.setReason(visibleSignatureProperties.getSignatureReason());
            }
            
            // the signing date, needed for valid signature
            signature.setSignDate(Calendar.getInstance());

            // do not set SignatureInterface instance, if external signing used
            SignatureInterface signatureInterface = isExternalSigning() ? null : this;

            // register signature dictionary and sign interface
            if (visibleSignatureProperties != null && visibleSignatureProperties.isVisualSignEnabled())
            {
                signatureOptions = new SignatureOptions();
                signatureOptions.setVisualSignature(visibleSignatureProperties.getVisibleSignature());
                signatureOptions.setPage(visibleSignatureProperties.getPage() - 1);
                doc.addSignature(signature, signatureInterface, signatureOptions);
            }
            else
            {
                doc.addSignature(signature, signatureInterface);
            }

            if (isExternalSigning())
            {
                System.out.println("Signing externally " + signedFile.getName());
                ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(fos);
                // invoke external signature service
                byte[] cmsSignature = sign(externalSigning.getContent());

                // Explanation of late external signing (off by default):
                // If you want to add the signature in a separate step, then set an empty byte array
                // and call signature.getByteRange() and remember the offset signature.getByteRange()[1]+1.
                // you can write the ascii hex signature at a later time even if you don't have this
                // PDDocument object anymore, with classic java file random access methods.
                // If you can't remember the offset value from ByteRange because your context has changed,
                // then open the file with PDFBox, find the field with findExistingSignature() or
                // PODDocument.getLastSignatureDictionary() and get the ByteRange from there.
                // Close the file and then write the signature as explained earlier in this comment.
                if (isLateExternalSigning())
                {
                    // this saves the file with a 0 signature
                    externalSigning.setSignature(new byte[0]);

                    // remember the offset (add 1 because of "<")
                    int offset = signature.getByteRange()[1] + 1;

                    // now write the signature at the correct offset without any PDFBox methods
                    try (RandomAccessFile raf = new RandomAccessFile(signedFile, "rw"))
                    {
                        raf.seek(offset);
                        raf.write(Hex.getBytes(cmsSignature));
                    }
                }
                else
                {
                    // set signature bytes received from the service and save the file
                    externalSigning.setSignature(cmsSignature);
                }
            }
            else
            {
                // write incremental (only for signing purpose)
                doc.saveIncremental(fos);
            }
        }
        
        // Do not close signatureOptions before saving, because some COSStream objects within
        // are transferred to the signed document.
        // Do not allow signatureOptions get out of scope before saving, because then the COSDocument
        // in signature options might by closed by gc, which would close COSStream objects prematurely.
        // See https://issues.apache.org/jira/browse/PDFBOX-3743
        IOUtils.closeQuietly(signatureOptions);
    }

    // Find an existing signature (assumed to be empty). You will usually not need this.
    private PDSignature findExistingSignature(PDDocument doc, String sigFieldName)
    {
        PDSignature signature = null;
        PDSignatureField signatureField;
        PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
        if (acroForm != null)
        {
            signatureField = (PDSignatureField) acroForm.getField(sigFieldName);
            if (signatureField != null)
            {
                // retrieve signature dictionary
                signature = signatureField.getSignature();
                if (signature == null)
                {
                    signature = new PDSignature();
                    // after solving PDFBOX-3524
                    // signatureField.setValue(signature)
                    // until then:
                    signatureField.getCOSObject().setItem(COSName.V, signature);
                }
                else
                {
                    throw new IllegalStateException("The signature field " + sigFieldName + " is already signed.");
                }
            }
        }
        return signature;
    }

    /**
     * Arguments are
     * [0] key store
     * [1] pin
     * [2] document that will be signed
     * [3] image of visible signature
     *
     * @param args
     * @throws java.security.KeyStoreException
     * @throws java.security.cert.CertificateException
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.UnrecoverableKeyException
     */
    public static void main(String[] args) throws KeyStoreException, CertificateException,
            IOException, NoSuchAlgorithmException, UnrecoverableKeyException
    {
        // generate with
        // keytool -storepass 123456 -storetype PKCS12 -keystore file.p12 -genkey -alias client -keyalg RSA
        if (args.length < 4)
        {
            usage();
            System.exit(1);
        }

        String tsaUrl = null;
        // External signing is needed if you are using an external signing service, e.g. to sign
        // several files at once.
        boolean externalSig = false;
        for (int i = 0; i < args.length; i++)
        {
            if ("-tsa".equals(args[i]))
            {
                i++;
                if (i >= args.length)
                {
                    usage();
                    System.exit(1);
                }
                tsaUrl = args[i];
            }
            if ("-e".equals(args[i]))
            {
                externalSig = true;
            }
        }

        File ksFile = new File(args[0]);
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        char[] pin = args[1].toCharArray();
        keystore.load(new FileInputStream(ksFile), pin);

        File documentFile = new File(args[2]);

        VisibleSignature signing = new VisibleSignature(keystore, pin.clone());

        File signedDocumentFile;
        int page;
        try (InputStream imageStream = new FileInputStream(args[3]))
        {
            String name = documentFile.getName();
            String substring = name.substring(0, name.lastIndexOf('.'));
            signedDocumentFile = new File(documentFile.getParent(), substring + "_signed.pdf");
            // page is 1-based here
            page = 1;
            signing.setVisibleSignDesigner(args[2], 0, 0, -50, imageStream, page);
        }
        signing.setVisibleSignatureProperties("name", "location", "Security", 0, page, true);
        signing.setExternalSigning(externalSig);
        signing.signPDF(documentFile, signedDocumentFile, tsaUrl);
    }

    /**
     * This will print the usage for this program.
     */
    private static void usage()
    {
        System.err.println("Usage: java " + VisibleSignature.class.getName()
                + " <pkcs12-keystore-file> <pin> <input-pdf> <sign-image>\n" + "" +
                           "options:\n" +
                           "  -tsa <url>    sign timestamp using the given TSA server\n"+
                           "  -e            sign using external signature creation scenario");
    }


    //
    // Additions by the OP
    //
    public static byte[] sign(PDDocument doc, String CERT_FILE, char[] ALIAS_PASS, String IMAGE_FILE) throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            IOException, UnrecoverableKeyException {
        System.out.println("Document pages ? " + doc.getNumberOfPages());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
//        ks.load(VisibleSignature.class.getResourceAsStream(CERT_FILE), ALIAS_PASS);
        ks.load(new FileInputStream(CERT_FILE), ALIAS_PASS);
        System.out.println("KeyStore is null ? " + (ks == null));
        VisibleSignature vs = new VisibleSignature(ks, ALIAS_PASS.clone());
        InputStream is = VisibleSignature.class.getResourceAsStream(IMAGE_FILE);
        int page = 1;
        vs.setVisibleSignDesigner(doc, 0, 0, -50, is, page);
        is.close();
        vs.setVisibleSignatureProperties("Test", "Test", "Test", 0, page, true);
        PDSignature signature = new PDSignature();
        PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
        System.out.println("Acroform is null ? " + (acroForm == null));
        if (acroForm != null)
            System.out.println("Acroform getNeedAppearances ? " + (acroForm.getNeedAppearances()));
        if (acroForm != null && acroForm.getNeedAppearances())
            if (acroForm.getFields().isEmpty())
                acroForm.getCOSObject().removeItem(COSName.NEED_APPEARANCES);
            else
                System.out.println("/NeedAppearances is set, signature may be ignored by Adobe Reader");
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        if (vs.visibleSignatureProperties != null) {
            vs.visibleSignatureProperties.buildSignature();
            signature.setName(vs.visibleSignatureProperties.getSignerName());
            signature.setLocation(vs.visibleSignatureProperties.getSignerLocation());
            signature.setReason(vs.visibleSignatureProperties.getSignatureReason());
            System.out.println("SignerName " + vs.visibleSignatureProperties.getSignerName());
        }
        signature.setSignDate(Calendar.getInstance());
        vs.signatureOptions = new SignatureOptions();
        vs.signatureOptions.setVisualSignature(vs.visibleSignatureProperties.getVisibleSignature());
        vs.signatureOptions.setPage(vs.visibleSignatureProperties.getPage() - 1);
//        doc.addSignature(signature, vs.signatureOptions);
        // using this overload instead fixes the issue.
        doc.addSignature(signature, vs, vs.signatureOptions);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.saveIncremental(baos);
        doc.close();
        IOUtils.closeQuietly(vs.signatureOptions);
        byte[] content = baos.toByteArray();
        System.out.println("Content length: >>>>>>>>>>>>>>>>>>> " + content.length);
        return content;
    }

    public void setVisibleSignDesigner(PDDocument doc, int x, int y, int zoomPercent, 
            InputStream imageStream, int page) 
            throws IOException
    {
        visibleSignDesigner = new PDVisibleSignDesigner(doc, imageStream, page);
        visibleSignDesigner.xAxis(x).yAxis(y).zoom(zoomPercent).adjustForRotation();
    }
}

