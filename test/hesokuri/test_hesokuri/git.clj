; Copyright (C) 2013 Google Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns hesokuri.test-hesokuri.git
  (:require clojure.java.io)
  (:use clojure.test
        hesokuri.git
        hesokuri.testing.temp))

(deftest test-default-git (is (git? default-git)))

(deftest test-git-false
  (are [x] (not (git? x))
       42
       "git"
       {}
       {:not-path "git"}
       {:path 100}
       {:path ""}
       {:path "git" :extra-key "not allowed"}))

(deftest test-git-true
  (are [x] (git? x)
       {:path "git"}
       {:path "/home/jdoe/bin/my-git"}))

(deftest test-invoke-result-false
  (are [x] (not (invoke-result? x))
       ""
       #{}
       {}
       {}
       {:exit 0 :out "a"}
       {:out "a" :err "a"}
       {:out "a" :err "a"}
       {:exit 0 :out "a" :err 42}
       {:exit 0 :out "a" :err "a" :extra-key "not allowed"}))

(deftest test-invoke-result-true
  (are [x] (invoke-result? x)
       {:exit 0 :out "a" :err "b"}
       {:exit -1 :out "" :err " "}))

(deftest test-args-false
  (are [x] (not (args? x))
       0
       {}
       {"a" "b"}
       [:a :b]
       [" " nil]))

(deftest test-args-true
  (are [x] (args? x)
       '()
       (lazy-seq ["a" "b" "c"])
       [""]
       ["init"]
       ["rev-parse"]
       '("checkout" "branch")))

(deftest test-invoke
  (with-temp-repo [repo-dir git-dir-flag]
    (let [rev-parse-result (invoke default-git [git-dir-flag "rev-parse"])]
      (is (= rev-parse-result {:err "" :out "" :exit 0}))
      (is (invoke-result? rev-parse-result)))))

(deftest test-invoke-streams-empty-err
  (with-temp-repo [repo-dir git-dir-flag]
    (let [[in out result]
          (invoke-streams
           default-git [git-dir-flag "hash-object" "-w" "--stdin"])]
      (spit in "hello\n")
      (is (not (realized? result)))
      (.close in)
      (is (= "ce013625030ba8dba906f756967f9e9ca394464a\n" (slurp out)))
      (is (= {:err "" :exit 0}) @result))))

(deftest test-invoke-streams-err
  (with-temp-repo [repo-dir git-dir-flag]
    (let [[_ _ result]
          (invoke-streams
           default-git [git-dir-flag "cat-file" "-t" "1234567"])]
      (is (= {:err "fatal: Not a valid object name 1234567\n" :exit 128}
             @result)))))

(deftest test-summary
  (let [err "[stderr contents]"
        out "[stdout contents]"
        exit 96
        result (summary ["arg1!" "?arg2"] {:err err :out out :exit exit})
        expected-substrs [err out (str exit) "git arg1! ?arg2"]]
    (doseq [substr expected-substrs]
      (is (not= -1 (.indexOf result substr)) substr))))

(deftest test-invoke-with-summary
  (let [result (invoke-with-summary default-git ["--version"])]
    (is (invoke-result? (first result)))
    (is (string? (second result)))
    (is (= 2 (count result)))))
