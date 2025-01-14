/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package picard.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.programgroups.VariantManipulationProgramGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Takes a VCF file and a Sequence Dictionary (from a variety of file types) and updates the Sequence Dictionary in VCF.
 *
 * @author George Grant
 *
 */
@CommandLineProgramProperties(
        summary = "Takes a VCF and a second file that contains a sequence dictionary and updates the VCF with the new sequence dictionary.",
        oneLineSummary = "Takes a VCF and a second file that contains a sequence dictionary and updates the VCF with the new sequence dictionary.",
        programGroup = VariantManipulationProgramGroup.class)
@DocumentedFeature
public class UpdateVcfSequenceDictionary extends CommandLineProgram {
    @Argument(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME, doc = "Input VCF")
    public File INPUT;

    @Argument(shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc = "Output VCF to be written.")
    public File OUTPUT;

    @Argument(shortName = StandardOptionDefinitions.SEQUENCE_DICTIONARY_SHORT_NAME, doc = "A Sequence Dictionary (can be read from one of the " +
            "following file types (SAM, BAM, VCF, BCF, Interval List, Fasta, or Dict)")
    public File SEQUENCE_DICTIONARY;

    private final Log log = Log.getInstance(UpdateVcfSequenceDictionary.class);

    @Override
    protected int doWork() {
        IOUtil.assertFileIsReadable(INPUT);
        IOUtil.assertFileIsReadable(SEQUENCE_DICTIONARY);
        IOUtil.assertFileIsWritable(OUTPUT);

        final SAMSequenceDictionary samSequenceDictionary = SAMSequenceDictionaryExtractor.extractDictionary(SEQUENCE_DICTIONARY.toPath());

        final VCFFileReader fileReader = new VCFFileReader(INPUT, false);
        final VCFHeader fileHeader = fileReader.getFileHeader();

        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(samSequenceDictionary)
                .clearOptions();
        if (CREATE_INDEX)
            builder.setOption(Options.INDEX_ON_THE_FLY);

        try {
            builder.setOutputStream(new FileOutputStream(OUTPUT));
        } catch (final FileNotFoundException ex ) {
            throw new PicardException("Could not open " + OUTPUT.getAbsolutePath() + ": " + ex.getMessage(), ex);
        }

        final VariantContextWriter vcfWriter = builder.build();
        fileHeader.setSequenceDictionary(samSequenceDictionary);
        vcfWriter.writeHeader(fileHeader);

        final ProgressLogger progress = new ProgressLogger(log, 10000);
        final CloseableIterator<VariantContext> iterator = fileReader.iterator();
        while (iterator.hasNext()) {
            final VariantContext context = iterator.next();
            vcfWriter.add(context);
            progress.record(context.getContig(), context.getStart());
        }

        CloserUtil.close(iterator);
        CloserUtil.close(fileReader);
        vcfWriter.close();

        return 0;
    }
}
