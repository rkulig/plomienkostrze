-- Probe cleanup (roadmap S-01, phase 3): the test-flow diagnostic path is
-- replaced by the real news domain; drops the table together with the entity
-- and controller removed in the same deployment. Deliberately breaks schema
-- compatibility with probe-era revisions (accepted in the plan).
DROP TABLE test_messages;
