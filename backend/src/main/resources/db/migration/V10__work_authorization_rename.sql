-- "nationality_note" sat on the recruiter-visible profile while "nationality" lived in
-- the separated demographics table. Two similarly-named fields either side of a
-- deliberate privacy boundary is an invitation to conflate them - and a candidate could
-- have typed their nationality into the note, defeating the separation.
--
-- Right to work is a bona fide job requirement, so the field stays; it is renamed to say
-- exactly what it is and nothing more.
ALTER TABLE candidate_profiles RENAME COLUMN nationality_note TO work_authorization;
