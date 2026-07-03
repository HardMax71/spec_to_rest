service Preamble {

  predicate isValidURI(s: String) = s matches /^https?:\/\/[^\s\x00-\x1f\x7f]+/

  predicate isValidEmail(s: String) = s matches /^[^@\s]+@[^@\s]+\.[^@\s]+$/

}
