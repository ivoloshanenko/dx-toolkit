#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
# Copyright (C) 2013-2016 DNAnexus, Inc.
#
# This file is part of dx-toolkit (DNAnexus platform client libraries).
#
#   Licensed under the Apache License, Version 2.0 (the "License"); you may not
#   use this file except in compliance with the License. You may obtain a copy
#   of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#   License for the specific language governing permissions and limitations
#   under the License.

import os, unittest, tempfile, subprocess, shutil, time

import dxpy_testutil as testutil

import dxpy

@unittest.skipUnless(testutil.TEST_FUSE,
                     'skipping tests that would mount FUSE filesystems')
class TestDXFS(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if 'DXTEST_FUSE' not in os.environ:
            return
        cls.project_name = u"dxclient_test_pröject"
        cls.project_id = subprocess.check_output(u"dx new project '{p}' --brief".format(p=cls.project_name), shell=True).strip()
        dxpy.config["DX_PROJECT_CONTEXT_ID"] = cls.project_id
        dxpy.config["DX_CLI_WD"] = '/'
        cls.project = dxpy.DXProject(cls.project_id)
        dxpy.config.__init__(suppress_warning=True)

        subprocess.check_call(['dx', 'mkdir', 'foo'])
        subprocess.check_call(['dx', 'mkdir', 'bar'])
        dxpy.upload_local_file(__file__, wait_on_close=True)
        dxpy.new_dxrecord(name="A/B testing")

        cls.mountpoint = tempfile.mkdtemp()
        cls.mount_project = os.path.join(cls.mountpoint, cls.project_name)
        # TODO: redirect logs to someplace in case we need to debug
        # problems in these tests
        subprocess.check_call(['dx-mount', cls.mountpoint])

    @classmethod
    def tearDownClass(cls):
        try:
            subprocess.check_call(['fusermount', '-u', cls.mountpoint])
            subprocess.check_call(u"dx rmproject --yes {p}".format(p=cls.project_id), shell=True)
        except:
            pass

    def test_dxfs_operations(self):
        # FIXME: Make the mount live or add command to refresh it with remote changes
        #subprocess.check_call(['dx', 'mkdir', 'foo'])
        #subprocess.check_call(['dx', 'mkdir', 'bar'])
        #subprocess.check_call(['dx', 'mkdir', '-p', '/bar/baz'])

        self.assertEqual(set(os.listdir(self.mount_project)), set(['foo', 'bar', 'AB testing', os.path.basename(__file__)]))

        # Reading
        self.assertEqual(open(__file__).read(), open(os.path.join(self.mount_project, __file__)).read())

        # Moving
        #shutil.move(os.path.join(self.mountpoint, __file__), os.path.join(self.mountpoint, __file__+"2"))
        #self.assertEqual(set(os.listdir(self.mountpoint)), set(['foo', 'bar', os.path.basename(__file__+"2")]))
        #shutil.move(os.path.join(self.mountpoint, __file__+"2"), os.path.join(self.mountpoint, "foo"))
        #self.assertEqual(set(os.listdir(os.path.join(self.mountpoint, 'foo'))), set([os.path.basename(__file__+"2")]))
        #folder_listing = self.project.list_folder('/foo')
        #self.assertEqual(len(folder_listing['folders']), 0)
        #self.assertEqual(len(folder_listing['objects']), 1)
        #self.assertEqual(dxpy.get_handler(folder_listing['objects'][0]['id']).name, os.path.basename(__file__+"2"))
        #self.assertEqual(open(__file__).read(), open(os.path.join(self.mountpoint, 'foo', __file__+"2")).read())

    def test_dxfs_mkdir(self):
        os.mkdir(os.path.join(self.mount_project, 'xyz'))
        self.assertIn('/xyz', self.project.list_folder('/')['folders'])
        os.mkdir(os.path.join(self.mount_project, 'xyz', 'abc'))
        self.assertIn('/xyz/abc', self.project.list_folder('/xyz')['folders'])
        os.rmdir(os.path.join(self.mount_project, 'xyz', 'abc'))
        self.assertNotIn('/xyz/abc', self.project.list_folder('/xyz')['folders'])
        os.rmdir(os.path.join(self.mount_project, 'xyz'))
        self.assertNotIn('/xyz', self.project.list_folder('/')['folders'])

    #def test_dxfs_write(self):
    #    filename1 = os.path.join(self.mountpoint, 'foo', 'f1')
    #    with open(filename1, 'w') as fh:
    #        fh.write('0123456789ABCDEF'*256)
    #    subprocess.check_call(['dxfs', 'close', filename1, '--wait'])
    #    with open(filename1) as fh:
    #        d = fh.read()
    #        print len(d)
    #        self.assertEqual(d, '0123456789ABCDEF'*256, "File readback failed")

class TestDXFSMountAll(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        if 'DXTEST_FUSE' not in os.environ:
            return
        cls.projects = {}
        for i in range(4):
            #first two projects with same name
            project_name = u"dxclient_test_project" if i < 2 else u"dxclient_test_project" + str(i)
            project_id = subprocess.check_output(u"dx new project '{}' --brief".format(project_name), shell=True).strip()

            cls.projects[project_id] = project_name
        dxpy.config.__init__(suppress_warning=True)

        cls.mountpoint = tempfile.mkdtemp()
        subprocess.check_call(['dx-mount', '--all', cls.mountpoint])

    @classmethod
    def tearDownClass(cls):
        try:
            try:
                subprocess.check_call(['fusermount', '-u', cls.mountpoint])
            except:
                pass
            project_list = ' '.join(cls.projects.keys())
            subprocess.check_call(u"dx rmproject --yes {}".format(project_list), shell=True)
        except:
            pass

    def _get_root_folder(self, project_id):
        '''We need append project ID to folder name if multiple projects exist with same name'''
        if len([x for x in self.projects.values() if x == self.projects[project_id]]) > 1:
            return self.projects[project_id] + '.' + project_id
        else:
            return self.projects[project_id]

    def test_dxfs_root_folders(self):
        '''This test check mapping between projects and root folders'''
        os_folders = [x.decode('utf-8') for x in os.listdir(self.mountpoint)]
        dx_folders = [self._get_root_folder(x) for x in self.projects.keys()]
        self.assertTrue(set(os_folders).issuperset(set(dx_folders)))
        pass

    def test_dxfs_folder_operations(self):
        '''This test check basic operations with folders'''
        project_id = self.projects.keys()[0]
        dxpy.config["DX_PROJECT_CONTEXT_ID"] = project_id
        dxpy.config["DX_CLI_WD"] = '/'
        #project = dxpy.DXProject(project_id)
        dxpy.config.__init__(suppress_warning=True)

        # create via DX
        subprocess.check_call(['dx', 'mkdir', 'foo'])
        subprocess.check_call(['dx', 'mkdir', 'bar'])
        time.sleep(10) #wait for FS sync (5sec default)
        self.assertTrue(set(os.listdir(os.path.join(self.mountpoint, self._get_root_folder(project_id)))).issuperset(
                        set(['foo', 'bar'])))

        # create via FS
        path = os.path.join(self.mountpoint, self._get_root_folder(project_id), 'foo2')
        os.mkdir(path)
        time.sleep(5)
        dx_folders = [x.strip('/') for x in subprocess.check_output(['dx', 'ls', project_id]).split()]
        self.assertTrue('foo2' in dx_folders)

        # remove via DX
        subprocess.check_call(['dx', 'rmdir', 'foo2'])
        time.sleep(10)
        self.assertFalse('foo2' in os.listdir(os.path.join(self.mountpoint, self._get_root_folder(project_id))))

    def test_dxfs_file_operations(self):
        pass

    def test_dxfs_object_filder(self):
        '''Special project objects such as records, genomic tables, applets, workflows, etc.,
        should not listed in the directory view of a project.'''
        project_id = self.projects.keys()[0]
        subprocess.check_call(['dx', 'new', 'record', '--close', project_id + ':test-record'])
        time.sleep(10)
        self.assertFalse('test-record' in os.listdir(os.path.join(self.mountpoint, self._get_root_folder(project_id))))


if __name__ == '__main__':
    unittest.main()
