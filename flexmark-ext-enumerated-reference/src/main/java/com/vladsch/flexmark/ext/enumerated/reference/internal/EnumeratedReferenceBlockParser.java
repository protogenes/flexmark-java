package com.vladsch.flexmark.ext.enumerated.reference.internal;

import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.BlockContent;
import com.vladsch.flexmark.ext.enumerated.reference.EnumeratedReferenceBlock;
import com.vladsch.flexmark.ext.enumerated.reference.EnumeratedReferenceExtension;
import com.vladsch.flexmark.parser.block.*;
import com.vladsch.flexmark.util.options.DataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnumeratedReferenceBlockParser extends AbstractBlockParser {
    static String ENUM_REF_ID = ".*";
    static Pattern ENUM_REF_ID_PATTERN = Pattern.compile("\\[[\\@|#]\\s*(" + ENUM_REF_ID + ")\\s*\\]");
    static Pattern ENUM_REF_DEF_PATTERN = Pattern.compile("^\\[[\\@]\\s*(" + ENUM_REF_ID + ")\\s*\\]:");

    private final EnumeratedReferenceBlock block = new EnumeratedReferenceBlock();
    private final EnumeratedReferenceOptions options;
    private final int contentOffset;
    private BlockContent content = new BlockContent();

    public EnumeratedReferenceBlockParser(EnumeratedReferenceOptions options, int contentOffset) {
        this.options = options;
        this.contentOffset = contentOffset;
    }

    public BlockContent getBlockContent() {
        return content;
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        final int nonSpaceIndex = state.getNextNonSpaceIndex();
        if (state.isBlank()) {
            if (block.getFirstChild() == null) {
                // Blank line after empty list item
                return BlockContinue.none();
            } else {
                return BlockContinue.atIndex(nonSpaceIndex);
            }
        }

        if (state.getIndent() >= options.contentIndent) {
            int contentIndent = state.getIndex() + options.contentIndent;
            return BlockContinue.atIndex(contentIndent);
        } else {
            return BlockContinue.none();
        }
    }

    @Override
    public void addLine(ParserState state, BasedSequence line) {
        content.add(line, state.getIndent());
    }

    @Override
    public void closeBlock(ParserState state) {
        // set the enumeratedReference from closingMarker to end
        block.setCharsFromContent();
        block.setEnumeratedReference(block.getChars().subSequence(block.getClosingMarker().getEndOffset() - block.getChars().getStartOffset()).trimStart());
        content = null;

        // add block to reference repository
        final EnumeratedReferenceRepository enumeratedReferences = EnumeratedReferenceExtension.ENUMERATED_REFERENCES.getFrom(state.getProperties());
        enumeratedReferences.put(block.getText().toString(), block);
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public boolean canContain(ParserState state, BlockParser blockParser, Block block) {
        return true;
    }

    public static class Factory implements CustomBlockParserFactory {
        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getAfterDependents() {
            return null;
        }

        @Override
        public Set<Class<? extends CustomBlockParserFactory>> getBeforeDependents() {
            return null;
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }

        @Override
        public BlockParserFactory create(DataHolder options) {
            return new BlockFactory(options);
        }
    }

    private static class BlockFactory extends AbstractBlockParserFactory {
        private final EnumeratedReferenceOptions options;

        private BlockFactory(DataHolder options) {
            super(options);
            this.options = new EnumeratedReferenceOptions(options);
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            if (state.getIndent() >= 4) {
                return BlockStart.none();
            }

            BasedSequence line = state.getLine();
            int nextNonSpace = state.getNextNonSpaceIndex();

            BasedSequence trySequence = line.subSequence(nextNonSpace, line.length());
            Matcher matcher = ENUM_REF_DEF_PATTERN.matcher(trySequence);
            if (matcher.find()) {
                // abbreviation definition
                int openingStart = nextNonSpace + matcher.start();
                int openingEnd = nextNonSpace + matcher.end();
                BasedSequence openingMarker = line.subSequence(openingStart, openingStart + 2);
                BasedSequence text = line.subSequence(openingStart + 2, openingEnd - 2).trim();
                BasedSequence closingMarker = line.subSequence(openingEnd - 2, openingEnd);

                int contentOffset = options.contentIndent;

                EnumeratedReferenceBlockParser enumeratedReferenceBlockParser = new EnumeratedReferenceBlockParser(options, contentOffset);
                enumeratedReferenceBlockParser.block.setOpeningMarker(openingMarker);
                enumeratedReferenceBlockParser.block.setText(text);
                enumeratedReferenceBlockParser.block.setClosingMarker(closingMarker);

                return BlockStart.of(enumeratedReferenceBlockParser)
                        .atIndex(openingEnd);
            } else {
                return BlockStart.none();
            }
        }
    }
}