// Copyright (C) 2013-2016 DNAnexus, Inc.
//
// This file is part of dx-toolkit (DNAnexus platform client libraries).
//
//   Licensed under the Apache License, Version 2.0 (the "License"); you may
//   not use this file except in compliance with the License. You may obtain a
//   copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
//   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
//   License for the specific language governing permissions and limitations
//   under the License.

package com.dnanexus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.dnanexus.DXDataObject.DescribeOptions;
import com.dnanexus.DXFile.Builder;
import com.dnanexus.DXFile.Describe;
import com.dnanexus.exceptions.InvalidStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

public class DXFileTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private DXProject testProject;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        testProject = DXProject.newProject().setName("DXFileTest").build();
    }

    @After
    public void tearDown() {
        if (testProject != null) {
            testProject.destroy();
        }
    }

    @Test
    public void testBuilder() {
        DXDataObjectTest.testBuilder(testProject,
                new DXDataObjectTest.BuilderFactory<DXFile.Builder, DXFile>() {
                    @Override
                    public DXFile.Builder getBuilder() {
                        return DXFile.newFile();
                    }
                });

        DXFile file =
                DXFile.newFile().setProject(testProject).setMediaType("application/json").build();
        Assert.assertEquals("application/json", file.describe().getMediaType());
    }

    @Test
    public void testCreateFileSerialization() throws IOException {
        Assert.assertEquals(
                DXJSON.parseJson("{\"project\":\"project-000011112222333344445555\", \"name\": \"foo\", \"media\": \"application/json\"}"),
                mapper.valueToTree(DXFile.newFile()
                        .setProject(DXProject.getInstance("project-000011112222333344445555"))
                        .setName("foo").setMediaType("application/json").buildRequestHash()));
    }

    @Test
    public void testCreateFileSimple() {
        DXFile f = DXFile.newFile().setProject(testProject).setName("foo").build();
        Describe describe = f.describe();
        Assert.assertEquals("foo", describe.getName());
    }

    @Test
    public void testCustomFields() {
        DXFile f = DXFile.newFile().setProject(testProject).setName("test").setMediaType("foo/bar")
                .build();

        // Retrieve some fields and verify that the ones we want are there and the ones we don't
        // want are not there
        DXFile.Describe describe = f.describe(DescribeOptions.get().withCustomFields(
                ImmutableList.of("media")));

        Assert.assertEquals("foo/bar", describe.getMediaType());
        try {
            describe.getName();
            Assert.fail("Expected getName to fail with IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Now describe with some complementary fields and perform the same check
        describe = f.describe(DescribeOptions.get().withCustomFields(ImmutableList.of("name")));

        Assert.assertEquals("test", describe.getName());
        try {
            describe.getMediaType();
            Assert.fail("Expected getMediaType to fail with IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testDataObjectMethods() {
        DXDataObjectTest.BuilderFactory<DXFile.Builder, DXFile> builderFactory =
                new DXDataObjectTest.BuilderFactory<DXFile.Builder, DXFile>() {
                    @Override
                    public DXFile.Builder getBuilder() {
                        return DXFile.newFile();
                    }
                };
        DXDataObjectTest.testOpenDataObjectMethods(testProject, builderFactory);
        // TODO: test out closed data object methods too
    }

    @Test
    public void testDescribeWithOptions() {
        DXFile f =
                DXFile.newFile().setProject(testProject).setName("test").setMediaType("foo/bar")
                        .build();
        Describe describe = f.describe(DescribeOptions.get());
        Assert.assertEquals("test", describe.getName());
        Assert.assertEquals("foo/bar", describe.getMediaType());
    }

    @Test
    public void testDownloadFails() throws IOException {
        DXFile f = DXFile.newFile().setProject(testProject).build();

        // Nothing uploaded to the file
        // The file cannot be downloaded because it is not in the 'closed' state
        thrown.expect(InvalidStateException.class);
        f.downloadBytes();
    }

    @Test
    public void testDownloadRamp() throws IOException {
        // File size is 65536
        byte[] uploadBytes = new byte[64 * 1024];
        new Random().nextBytes(uploadBytes);

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();

        byte[] bytesFromDownloadStream = new byte[64 * 1024];
        InputStream is = f.downloadStream();
        // read => 65536 bytes
        is.read(bytesFromDownloadStream, 0, 64 * 1024);

        Assert.assertArrayEquals(uploadBytes, bytesFromDownloadStream);

        // File size is 65537 bytes
        uploadBytes = new byte[64 * 1024 + 1];
        new Random().nextBytes(uploadBytes);
        f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();

        bytesFromDownloadStream = new byte[64 * 1024 + 1];
        is = f.downloadStream();
        // read => 65536 bytes
        is.read(bytesFromDownloadStream, 0, 64 * 1024);
        // read again => 1 byte
        is.read(bytesFromDownloadStream, 64 * 1024, 1);

        Assert.assertArrayEquals(uploadBytes, bytesFromDownloadStream);

        // File size is 393216
        uploadBytes = new byte[393216];
        new Random().nextBytes(uploadBytes);
        f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();

        bytesFromDownloadStream = new byte[393216];
        is = f.downloadStream();
        // read => 65536 bytes
        is.read(bytesFromDownloadStream, 0, 64 * 1024);
        // read again => 65536 bytes
        is.read(bytesFromDownloadStream, 64 * 1024, 64 * 1024);
        // read again => 65536 bytes
        is.read(bytesFromDownloadStream, 2 * 64 * 1024, 64 * 1024);
        // read again => 65536 bytes
        is.read(bytesFromDownloadStream, 3 * 64 * 1024, 64 * 1024);
        // read again => 131072 bytes
        is.read(bytesFromDownloadStream, 4 * 64 * 1024, 2 * 64 * 1024);

        Assert.assertArrayEquals(uploadBytes, bytesFromDownloadStream);
    }

    @Test
    public void testDownloadReadReturnsCorrectValue() throws IOException {
        byte[] uploadBytes = new byte[64 * 1024];
        new Random().nextBytes(uploadBytes);
        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();

        byte[] bytesFromDownloadStream = new byte[64 * 1024];
        InputStream is = f.downloadStream();
        // read => 32 kb
        int bytesRead = is.read(bytesFromDownloadStream, 0, 32 * 1024);
        Assert.assertEquals(32 * 1024, bytesRead);

        // read => 0 bytes
        bytesRead = is.read(bytesFromDownloadStream, 32 * 1024, 0);
        Assert.assertEquals(0, bytesRead);

        // read => 32 kb
        bytesRead = is.read(bytesFromDownloadStream, 32 * 1024, 32 * 1024);
        Assert.assertEquals(32 * 1024, bytesRead);

        // read => 0 bytes
        bytesRead = is.read(bytesFromDownloadStream, 64 * 1024, 0);
        Assert.assertEquals(0, bytesRead);

        // end of file
        bytesRead = is.read(bytesFromDownloadStream, 0, 64 * 1024);
        Assert.assertEquals(-1, bytesRead);
    }

    @Test
    public void testGetInstance() {
        DXFile file = DXFile.getInstance("file-000000000000000000000000");
        Assert.assertEquals("file-000000000000000000000000", file.getId());
        Assert.assertEquals(null, file.getProject());

        DXFile file2 =
                DXFile.getInstance("file-000000000000000000000001",
                        DXProject.getInstance("project-123412341234123412341234"));
        Assert.assertEquals("file-000000000000000000000001", file2.getId());
        Assert.assertEquals("project-123412341234123412341234", file2.getProject().getId());

        try {
            DXFile.getInstance(null);
            Assert.fail("Expected creation without setting ID to fail");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            DXFile.getInstance("file-123412341234123412341234", (DXContainer) null);
            Assert.fail("Expected creation without setting project to fail");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            DXFile.getInstance(null, DXProject.getInstance("project-123412341234123412341234"));
            Assert.fail("Expected creation without setting ID to fail");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testUploadBytesDownloadBytes() throws IOException {
        // With string data
        String uploadData = "Test";
        byte[] uploadBytes = uploadData.getBytes();

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();
        byte[] downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);

        // Download again
        downloadBytes = f.downloadBytes();
        Assert.assertArrayEquals(uploadBytes, downloadBytes);
    }

    @Test
    public void testUploadChunks() throws IOException {
        // Upload 7mb
        byte[] uploadBytes = new byte[7 * 1024 * 1024];
        new Random().nextBytes(uploadBytes);

        DXFile f = DXFile.newFile().setProject(testProject).build();
        // Max chunk size 5mb
        f.uploadChunkSize = 5 * 1024 * 1024;
        f.upload(uploadBytes);
        f.closeAndWait();
        byte[] downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);
    }

    @Test
    public void testUploadDownloadBinary() throws IOException {
        String uploadData = Integer.toBinaryString(12345678);
        byte[] uploadBytes = uploadData.getBytes();

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();
        byte[] downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);
    }

    @Test
    public void testUploadDownloadBuilder() throws IOException {
        // Upload bytes
        String uploadData = "Test";
        byte[] uploadBytes = uploadData.getBytes();

        DXFile f = DXFile.newFile().setProject(testProject).upload(uploadBytes).build().closeAndWait();
        byte[] downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);

        // Upload stream
        InputStream uploadStream = IOUtils.toInputStream(uploadData);

        f = DXFile.newFile().setProject(testProject).upload(uploadStream).build().closeAndWait();
        downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);

        // Upload bytes with empty string
        uploadBytes = new byte[0];

        f = DXFile.newFile().setProject(testProject).upload(uploadBytes).build().closeAndWait();
        downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);

        // Upload stream with empty string
        uploadStream = new ByteArrayInputStream(uploadBytes);

        f = DXFile.newFile().setProject(testProject).upload(uploadStream).build().closeAndWait();
        downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);
    }

    @Test
    public void testUploadDownloadEmpty() throws IOException {
        // Upload bytes, download bytes
        byte[] uploadBytes = new byte[0];

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadBytes);
        f.closeAndWait();
        byte[] downloadBytes = f.downloadBytes();

        Assert.assertArrayEquals(uploadBytes, downloadBytes);

        // Upload stream, download stream
        InputStream uploadStream = new ByteArrayInputStream(uploadBytes);

        f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadStream);
        f.closeAndWait();

        byte[] bytesFromDownloadStream = IOUtils.toByteArray(f.downloadStream());

        Assert.assertArrayEquals(uploadBytes, bytesFromDownloadStream);
    }

    public void testUploadNullBytesBuilderFails() {
        Builder b = DXFile.newFile().setProject(testProject);
        thrown.expect(NullPointerException.class);
        b.upload((byte[]) null);
    }

    @Test
    public void testUploadNullBytesFails() throws IOException {
        DXFile f = DXFile.newFile().setProject(testProject).build();
        thrown.expect(NullPointerException.class);
        f.upload().write((byte []) null);
    }

    @Test
    public void testUploadNullStreamBuilderFails() {
        Builder b = DXFile.newFile().setProject(testProject);
        thrown.expect(NullPointerException.class);
        b.upload((InputStream) null);
    }

    @Test
    public void testUploadNullStreamFails() throws IOException {
        DXFile f = DXFile.newFile().setProject(testProject).build();
        thrown.expect(NullPointerException.class);
        f.upload((InputStream) null);
    }

    @Test
    public void testUploadStreamDownloadOutputStream() throws IOException {
        // With string data
        String uploadData = "Test";
        InputStream uploadStream = IOUtils.toInputStream(uploadData);

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadStream);
        f.closeAndWait();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        f.downloadToOutputStream(bos);
        byte[] bytesFromDownloadStream = bos.toByteArray();

        Assert.assertArrayEquals(uploadData.getBytes(), bytesFromDownloadStream);
    }

    @Test
    public void testUploadStreamDownloadStream() throws IOException {
        // With string data
        String uploadData = "Test";
        InputStream uploadStream = IOUtils.toInputStream(uploadData);

        DXFile f = DXFile.newFile().setProject(testProject).build();
        f.upload(uploadStream);
        f.closeAndWait();
        byte[] bytesFromDownloadStream = IOUtils.toByteArray(f.downloadStream());

        Assert.assertArrayEquals(uploadData.getBytes(), bytesFromDownloadStream);

        // With larger sized data
        byte[] uploadBytes = new byte[10 * 1024 * 1024];
        new Random().nextBytes(uploadBytes);

        f = DXFile.newFile().setProject(testProject).build();
        f.uploadChunkSize = 7 * 1024 * 1024;
        f.upload(new ByteArrayInputStream(uploadBytes));
        f.closeAndWait();
        bytesFromDownloadStream = IOUtils.toByteArray(f.downloadStream());

        Assert.assertArrayEquals(uploadBytes, bytesFromDownloadStream);
    }
}
