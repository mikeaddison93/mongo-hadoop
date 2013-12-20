/*
 * Copyright 2010-2013 10gen Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mongodb.hadoop;

import com.mongodb.hadoop.input.BSONFileRecordReader;
import com.mongodb.hadoop.splitter.BSONSplitter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.util.ReflectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class BSONFileInputFormat extends FileInputFormat {

    private static final Log LOG = LogFactory.getLog(BSONFileInputFormat.class);

    @Override
    public RecordReader createRecordReader(final InputSplit split, final TaskAttemptContext context)
        throws IOException, InterruptedException {
        
        BSONFileRecordReader reader = new BSONFileRecordReader();
        reader.initialize(split, context);
        return reader;
    }

    public static PathFilter getInputPathFilter(final JobContext context) {
        Configuration conf = context.getConfiguration();
        Class<?> filterClass = conf.getClass("bson.pathfilter.class", null,
                                             PathFilter.class);
        return filterClass != null ? (PathFilter) ReflectionUtils.newInstance(filterClass, conf) : null;
    }

    @Override
    public List<FileSplit> getSplits(final JobContext context) throws IOException {
        Configuration config = context.getConfiguration();
        PathFilter pf = getInputPathFilter(context);
        ArrayList<FileSplit> splits = new ArrayList<FileSplit>();
        List<FileStatus> inputFiles = listStatus(context);
        for (FileStatus file : inputFiles) {
            if (pf != null && !pf.accept(file.getPath())) {
                LOG.info("skipping file " + file.getPath() + " not matched path filter.");
                continue;
            } else {
                LOG.info("processing file " + file.getPath());
            }

            BSONSplitter splitter = new BSONSplitter();
            splitter.setConf(config);
            splitter.setInputPath(file.getPath());
            Path splitFilePath = new Path(file.getPath().getParent(), "." + file.getPath().getName() + ".splits");
            try {
                splitter.loadSplitsFromSplitFile(file, splitFilePath);
            } catch (BSONSplitter.NoSplitFileException nsfe) {
                LOG.info("No split file for " + file + "; building split file");
                splitter.readSplitsForFile(file);
            }
            LOG.info("BSONSplitter found " + splitter.getAllSplits().size() + " splits.");
            splits.addAll(splitter.getAllSplits());
        }
        LOG.info("Total of " + splits.size() + " found.");
        return splits;
    }

}
