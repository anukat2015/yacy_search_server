// Segments.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.07.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
// $LastChangedBy: orbiter $
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.document.Condenser;
import de.anomic.document.Document;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class Segments implements Iterable<Segment> {
    
    /**
     * process enumeration type
     * defines constants that can be used to assign process-related segment names
     */
    public enum Process {
        
        RECEIPTS,
        QUERIES,
        DHTIN,
        DHTOUT,         // the only segment that is used for reading-only
        PROXY,
        LOCALCRAWLING,
        REMOTECRAWLING,
        PUBLIC;         // includes the index that can be retrieved by the yacy p2p api

        public String toString() {
            throw new UnsupportedOperationException("toString not allowed");
        }
    }
    
    private final Log log;
    private final File segmentsPath;
    private final int entityCacheMaxSize;
    private final long maxFileSize;
    private HashMap<String, Segment> segments;
    private HashMap<Process, String> process_assignment;
    private final boolean useTailCache;
    private final boolean exceed134217727;
    
    public Segments(
            final Log log,
            final File segmentsPath,
            final int entityCacheMaxSize,
            final long maxFileSize,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.log = log;
        this.segmentsPath = segmentsPath;
        this.entityCacheMaxSize = entityCacheMaxSize;
        this.maxFileSize = maxFileSize;
        this.useTailCache = useTailCache;
        this.exceed134217727 = exceed134217727;
        this.segments = new HashMap<String, Segment>();
        this.process_assignment = new HashMap<Process, String>();

        // assign default segment names for the processes
        this.process_assignment.put(Process.RECEIPTS,       "default");
        this.process_assignment.put(Process.QUERIES,        "default");
        this.process_assignment.put(Process.DHTIN,          "default");
        this.process_assignment.put(Process.DHTOUT,         "default");
        this.process_assignment.put(Process.PROXY,          "default");
        this.process_assignment.put(Process.LOCALCRAWLING,  "default");
        this.process_assignment.put(Process.REMOTECRAWLING, "default");
        this.process_assignment.put(Process.PUBLIC,         "default");
    }
    
    public void setSegment(Process process, String segmentName) {
        this.process_assignment.put(process, segmentName);
    }
    
    public static void migrateOld(File oldSingleSegment, File newSegmentsPath, String newSegmentName) {
        if (!oldSingleSegment.exists()) return;
        File newSegmentPath = new File(newSegmentsPath, newSegmentName);
        if (!newSegmentPath.exists()) newSegmentPath.mkdirs();
        Segment.migrateTextIndex(oldSingleSegment, newSegmentPath);
        Segment.migrateTextMetadata(oldSingleSegment, newSegmentPath);
        
        String[] oldFiles = oldSingleSegment.list();
        for (String oldFile: oldFiles) {
            if (oldFile.startsWith("text.")) {
                new File(oldSingleSegment, oldFile).renameTo(new File(newSegmentPath, oldFile));
            }
        }
    }
    
    public String[] segmentNames() {
        return this.segments.keySet().toArray(new String[this.segments.size()]);
    }
    
    public boolean segmentExist(final String segmentName) {
        return segments.containsKey(segmentName);
    }
    
    public Segment segment(final Process process) {
        return segment(this.process_assignment.get(process));
    }
    
    public Segment segment(final String segmentName) {
        Segment segment = segments.get(segmentName);
        if (segment == null) {
            // generate the segment
            try {
                segment = new Segment(
                        this.log,
                        new File(this.segmentsPath, segmentName),
                        this.entityCacheMaxSize,
                        this.maxFileSize,
                        this.useTailCache,
                        this.exceed134217727);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            this.segments.put(segmentName, segment);
        }
        return segment;
    }
    
    public int URLCount() {
        int c = 0;
        for (Segment s: this.segments.values()) c += s.urlMetadata().size();
        return c;
    }
    
    public int RWICount() {
        int c = 0;
        for (Segment s: this.segments.values()) c += s.termIndex().sizesMax();
        return c;
    }
    
    public int RWIBufferCount() {
        int c = 0;
        for (Segment s: this.segments.values()) c += s.termIndex().getBufferSize();
        return c;
    }
    
    public MetadataRepository urlMetadata(final Process process) {
        return segment(this.process_assignment.get(process)).urlMetadata();
    }

    public IndexCell<WordReference> termIndex(final Process process) {
        return segment(this.process_assignment.get(process)).termIndex();
    }
    
    public void clear(final Process process) {
        segment(this.process_assignment.get(process)).clear();
    }
    
    public File getLocation(final Process process) {
        return segment(this.process_assignment.get(process)).getLocation();
    }

    public void close(final Process process) {
        segment(this.process_assignment.get(process)).close();
    }
    
    public void close() {
        if (segments != null) for (Segment s: this.segments.values()) s.close();
        this.segments = null;
    }

    public void finalize() {
        this.close();
    }

    public URLMetadataRow storeDocument(
            final String segmentName,
            final yacyURL url,
            final yacyURL referrerURL,
            final Date docDate,
            final long sourcesize,
            final Document document,
            final Condenser condenser
            ) throws IOException {
        return segment(segmentName).storeDocument(
                url,
                referrerURL,
                docDate,
                sourcesize,
                document,
                condenser
         );
    }
    
    public synchronized Segment.ReferenceCleaner getReferenceCleaner(final String segmentName, final byte[] startHash) {
        return segment(segmentName).getReferenceCleaner(startHash);
    }

    public Iterator<Segment> iterator() {
        return this.segments.values().iterator();
    }
}

